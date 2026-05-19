package com.rajmani7584.payloaddumper.model

import android.app.Activity
import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.ByteString
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.engine.part_manifest.PartManifestOuterClass
import com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper
import com.rajmani7584.payloaddumper.ui.screens.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DataModel(application: Application): AndroidViewModel(application) {

    private val settingsDataStore = SettingsData(application)
    val externalStorage: String = Environment.getExternalStorageDirectory().absolutePath


    val isDarkTheme =
        settingsDataStore.darkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isDynamicColor =
        settingsDataStore.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val concurrency =
        settingsDataStore.concurrency.stateIn(viewModelScope, SharingStarted.Eagerly, 4)
    val autoDelete =
        settingsDataStore.autoDelete.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _hasPermission = mutableStateOf<Boolean?>(null)
    val hasPermission: State<Boolean?> = _hasPermission

    private val _url =
        mutableStateOf("")
    val remoteUrl: State<String> = _url

    fun setURL(value: String) {
        _url.value = value
    }

    fun setDarkTheme(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDarkTheme(value)
        }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDynamicColor(value)
        }
    }

    fun setConcurrency(value: Int) {
        viewModelScope.launch {
            settingsDataStore.saveConcurrency(value)
        }
    }

    fun setAutoDelete(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoDelete(value)
        }
    }

    fun setPermission(activity: MainActivity) {
        _hasPermission.value = Utils.hasPermission(activity)
    }

    fun requestPermission(activity: Activity?) {
        if (activity == null) return
        Utils.requestPermission(activity)
        _hasPermission.value = Utils.hasPermission(activity)
    }

    private val _lastDirectory = mutableStateOf(externalStorage)
    val lastDirectory: State<String> = _lastDirectory


    private val _outputDirectory = mutableStateOf("$externalStorage/PayloadDumper")
    val outputDirectory: State<String> = _outputDirectory

    fun setLastDirectory(path: String) {
        _lastDirectory.value = path
    }

    fun setOutputDirectory(currentPath: String) {
        _outputDirectory.value = currentPath
    }

    private val _payload = MutableStateFlow<PayloadState>(PayloadState.Idle)
    val payload: StateFlow<PayloadState> = _payload

    var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(16)
                val state = _payload.value as? PayloadState.Ready ?: break
                val updated = state.partitions.map { p ->
                    val base = p.id * StructLayout.STRIDE
                    val status = state.buffer.get(base + StructLayout.OFF_STAT).toInt() and 0xFF
                    val progress = state.buffer.get(base + StructLayout.OFF_PROG).toInt() and 0xFF
                    p.copy(
                        status = PartStatus.fromCode(status),
                        progress = progress / 100f
                    )
                }
                val newErrors = updated.filter { it.status == PartStatus.FAILED }.associate { it.id to PayloadDumper.getDumpError(it.id).getOrElse { "Unknown error" } }
                _payload.value = state.copy(partitions = updated, errors = state.errors + newErrors)
                if (updated.none { it.status == PartStatus.RUNNING || it.status == PartStatus.PENDING }) {
                    break
                }
            }
        }
    }

    private val _navEvent = Channel<Unit>(Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()

    suspend fun initPayload(
        payloadType: PayloadType
    ) {
        withContext(Dispatchers.IO) {
            _payload.value = PayloadState.Loading
            PayloadDumper.init().getOrElse {
                _payload.value = PayloadState.Error(it.message ?: "Engine initialisation error")
                return@withContext
            }
            PayloadDumper.open(payloadType).getOrElse {
                _payload.value =
                    PayloadState.Error(it.message ?: "Error occurred while opening the payload")
                Log.d("ERROR", it.message ?: "Unknown")
                return@withContext
            }
            val manifest = PayloadDumper.getManifest().getOrElse {
                _payload.value =
                    PayloadState.Error(it.message ?: "Error occurred while fetching manifest")
                return@withContext
            }

            val partitions = manifest.partitionsList.mapIndexed { index, p -> PartitionState(p?.partitionName ?: "error!", p?.newPartitionInfo?.size ?: 0L, p.incremental, p?.newPartitionInfo?.hash, index) }
            val buffer = ByteBuffer.allocateDirect(partitions.size * StructLayout.STRIDE)
                .apply { order(ByteOrder.nativeOrder()) }
            PayloadDumper.setupBuffer(partitions.size, buffer).getOrElse {
                _payload.value =
                    PayloadState.Error(it.message ?: "Error occurred while setting up buffer")
                return@withContext
            }
            val name = payloadType.getPathString().split("/").last()
            _payload.value = PayloadState.Ready(
                name,
                payloadType,
                partitions,
                manifest,
                buffer
            )
            _navEvent.send(Unit)
        }
    }

    fun init(payloadType: PayloadType) {
        viewModelScope.launch(Dispatchers.IO) {
            initPayload(payloadType)
        }
    }

    suspend fun getSignatures() {
        withContext(Dispatchers.IO) {
            PayloadDumper.getSignatures().onFailure {
                LogManager.error("Can't get signature: ${it.message}")
            }.onSuccess {
                val sig = it.signaturesList[0].data.toByteArray()
                    .joinToString("") { b -> "%02x".format(b) }
                val s = StringBuilder()
                sig.forEachIndexed { index, ch ->
                    if (index % 16 == 0) s.append("\n")
                    if (index % 2 == 0) s.append(" ")
                    s.append(ch)
                }

                LogManager.log(s.toString())
            }
        }
    }

    private fun updatePartition(index: Int, update: (PartitionState) -> PartitionState) {
        val state = _payload.value as? PayloadState.Ready ?: return
        _payload.value = state.copy(
            partitions = state.partitions.map { if (it.id == index) update(it) else it }
        )
    }

    private fun addError(index: Int, message: String) {
        val state = _payload.value as? PayloadState.Ready ?: return
        _payload.value = state.copy(errors = state.errors + (index to message))
    }

    suspend fun dump(partition: PartitionState) {
        withContext(Dispatchers.IO) {
            updatePartition(partition.id) { it.copy(status = PartStatus.PENDING) }
            val out = Utils.setupPartitionName(outputDirectory.value, partition.name, 0)
            PayloadDumper.dumpPart(partition.id, out)
                .onFailure { e ->
                    updatePartition(partition.id) { it.copy(status = PartStatus.FAILED) }
                    addError(partition.id, e.message ?: "Unknown error")
                    LogManager.error(e.message ?: "Unknown")
                    return@withContext
                }
            startPolling()
        }
    }

    suspend fun dumpAll() {
        withContext(Dispatchers.IO) {
            val state = _payload.value as? PayloadState.Ready ?: return@withContext
            state.partitions.forEach {
                dump(it)
            }
        }
    }

    suspend fun cancel(partition: PartitionState) {
        withContext(Dispatchers.IO) {
            val state = _payload.value as? PayloadState.Ready ?: return@withContext
            state.buffer.put(partition.id * StructLayout.STRIDE + StructLayout.OFF_ABT, 1)
            updatePartition(partition.id) { it.copy(status = PartStatus.FAILED) }
            PayloadDumper.cancelPart(partition.id)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            PayloadDumper.init()
                .onSuccess {
                    if (it != 0) {
                        return@onSuccess
                    }
                    LogManager.success("Engine initialized!")
                }
                .onFailure {
                    LogManager.error(it.message ?: "Unknown error occurred")
                }
        }
    }
}

sealed class PayloadState {
    object Idle: PayloadState()
    object Loading: PayloadState()
    data class Error(val message: String): PayloadState()
    data class Ready(
        val name: String,
        val payloadType: PayloadType,
        val partitions: List<PartitionState>,
        val manifest: PartManifestOuterClass.PartManifest,
        val buffer: ByteBuffer,
        val errors: Map<Int, String> = emptyMap()
    ): PayloadState()
}
data class PartitionState(val name: String, val size: Long, val incremental: Boolean, val hash: ByteString?, val id: Int, val status: PartStatus = PartStatus.IDLE, val progress: Float = 0f)

enum class PartStatus(val code: Int) {
    IDLE(0), PENDING(1), RUNNING(2), COMPLETED(3), FAILED(4);

    companion object {
        fun fromCode(value: Int) = PartStatus.entries.find { it.code == value } ?: IDLE
    }
}
object StructLayout {
    const val STRIDE = 8
//    const val OFF_ID = 0
    const val OFF_ABT = 1
    const val OFF_STAT = 2
    const val OFF_PROG = 4
}