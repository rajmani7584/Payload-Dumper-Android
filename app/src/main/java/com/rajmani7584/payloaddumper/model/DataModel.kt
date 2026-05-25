package com.rajmani7584.payloaddumper.model

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.ByteString
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.engine.part_manifest.PartManifestOuterClass
import com.rajmani7584.payloaddumper.engine.part_manifest.newPartitionInfoOrNull
import com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper
import com.rajmani7584.payloaddumper.ui.screens.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataModel(application: Application): AndroidViewModel(application) {
    val externalStorage: String = Environment.getExternalStorageDirectory().absolutePath

    private val _hasPermission = mutableStateOf<Boolean?>(null)
    val hasPermission: State<Boolean?> = _hasPermission

    private val _hasNotifyPermission = mutableStateOf<Boolean?>(null)
    val hasNotifyPermission: State<Boolean?> = _hasNotifyPermission

    private val _url =
        mutableStateOf("")
    val remoteUrl: State<String> = _url

    fun setURL(value: String) {
        _url.value = value
    }

    fun setPermissions(activity: MainActivity) {
        _hasPermission.value = Utils.hasPermission(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            _hasNotifyPermission.value = Utils.hasNotifyPermission(activity)
        }
    }

    fun requestPermission(activity: Activity) {
        Utils.requestPermission(activity)
        _hasPermission.value = Utils.hasPermission(activity)
    }

    fun requestNotifyPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Utils.requestNotifyPermission(activity)
            _hasNotifyPermission.value = Utils.hasNotifyPermission(activity)
        }
    }

    private val _lastDirectory = MutableStateFlow(externalStorage)
    val lastDirectory: StateFlow<String> = _lastDirectory


    private val _baseOutputDirectory = "$externalStorage/PayloadDumper"

    private val _outputDirectory = MutableStateFlow(_baseOutputDirectory)
    val outputDirectory: StateFlow<String> = _outputDirectory

    fun setLastDirectory(path: String) {
        _lastDirectory.value = path
    }

    fun setOutputDirectory(currentPath: String) {
        _outputDirectory.value = currentPath
    }

    private val _payload = MutableStateFlow<PayloadState>(PayloadState.Idle)
    val payload: StateFlow<PayloadState> = _payload

    var pollingJob: Job? = null

    private var settingParam = SettingParam()

    fun setSettingParam(param: SettingParam) {
        settingParam = param
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(100)
                val state = _payload.value as? PayloadState.Ready ?: break
                val updated = state.partitions.map { p ->
                    val base = p.id * StructLayout.STRIDE
                    val status = state.buffer.get(base + StructLayout.OFF_STAT).toInt() and 0xFF
                    val progress = state.buffer.get(base + StructLayout.OFF_PROG).toInt() and 0xFF
                    val pState = PartStatus.fromCode(status)

                    val alreadyFinalized = p.status == pState && pState.isFinal()

                    val error = if (!alreadyFinalized && (pState == PartStatus.FAILED || pState == PartStatus.VERIFICATION_FAILED)) p.error
                        ?: onFailure(p, PayloadDumper.getDumpError(p.id).getOrElse { "Unknown error" }) else p.error

                    if (!alreadyFinalized && pState == PartStatus.COMPLETED) LogManager.success("Dumped ${p.name}.img to ${p.output}")

                    p.copy(
                        status = pState,
                        progress = progress / 100f,
                        error = error
                    )
                }
                _payload.value = state.copy(partitions = updated)
                if (updated.all { it.status.isFinal() }) {
                    break
                }
            }
        }
    }
    fun onFailure(partition: PartitionState, message: String): String {
        LogManager.error("${partition.name}.img: $message")
        if (settingParam.autoDelete && partition.output != null) {
            try {
                if(File(partition.output).delete()) LogManager.log("Auto deleted: ${partition.output}")
            } catch (e: Exception) {
                LogManager.error(e.message ?: "Error auto deleting ${partition.output}")
            }
        }
        return message
    }
    private val _navEvent = Channel<Unit>(Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()

    suspend fun initPayload(payloadType: PayloadType) {
        LogManager.log("Opening payload: ${payloadType.getPathString()}")
        withContext(Dispatchers.IO) {
            _payload.value = PayloadState.Loading
            PayloadDumper.init().getOrElse {
                _payload.value = PayloadState.Error(it.message ?: "Engine initialisation error")
                return@withContext
            }
            val manifest = PayloadDumper.open(payloadType, settingParam.concurrency, settingParam.bufSize).getOrElse {
                _payload.value =
                    PayloadState.Error(it.message ?: "Error occurred while opening the payload")
                LogManager.error(it.message ?: "Error occurred while opening the payload")
                return@withContext
            }

            val partitions = manifest.partitionsList.mapIndexed { index, p ->
                PartitionState(
                    name = if (p.hasPartitionName()) p.partitionName else "N/A",
                    size = p.newPartitionInfoOrNull?.size ?: 0L,
                    incremental = p.incremental,
                    hash = p.newPartitionInfoOrNull?.hash,
                    downloadSize = if (p.hasDownloadSize()) p.downloadSize else null,
                    output = null,
                    error = null,
                    id = index
                )
            }
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
            val timestamp = SimpleDateFormat("yyyyMMDD-hhmmss", Locale.getDefault()).format(Date())
            _outputDirectory.value =
                "$_baseOutputDirectory/$timestamp-${name.substring(0, minOf(12, name.length))}"
            _navEvent.send(Unit)

            LogManager.success("Paylaod info fetched successfully")
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

//    private fun addError(index: Int, message: String) {
//        val state = _payload.value as? PayloadState.Ready ?: return
//        _payload.value = state.copy(errors = state.errors + (index to message))
//        LogManager.error(message)
//    }

    suspend fun dump(partition: PartitionState) {
        withContext(Dispatchers.IO) {
            if (_payload.value !is PayloadState.Ready) return@withContext
            if (partition.status == PartStatus.PENDING || partition.status == PartStatus.RUNNING) return@withContext
            val out = Utils.setupPartitionName(outputDirectory.value, partition.name, settingParam.overwrite)
            LogManager.log("Dumping ${partition.name}.img to $out")
            updatePartition(partition.id) { it.copy(status = PartStatus.PENDING, output = out, error = null) }
            PayloadDumper.dumpPart(partition.id, out, settingParam.verifyHash)
                .onFailure { e ->
                    updatePartition(partition.id) { it.copy(status = PartStatus.FAILED) }
                    LogManager.error(e.message ?: "Unknown")
                    if (settingParam.autoDelete) {
                        try {
                            if(File(out).delete()) LogManager.log("Auto deleted: $out")
                        } catch (e: Exception) {
                            LogManager.error(e.message ?: "Error auto deleting $out")
                        }
                    }
                }
            startPolling()
        }
    }

    suspend fun dumpAll() {
        withContext(Dispatchers.IO) {
            val state = _payload.value as? PayloadState.Ready ?: return@withContext
            state.partitions.forEach {
                if (it.status == PartStatus.PENDING || it.status == PartStatus.RUNNING) return@withContext
                dump(it)
            }
        }
    }

    suspend fun cancelAll() {
        withContext(Dispatchers.IO) {
            val state = _payload.value as? PayloadState.Ready ?: return@withContext
            state.partitions.forEach {
                if (it.status == PartStatus.PENDING || it.status == PartStatus.RUNNING)
                    cancel(it)
            }
        }
    }

    suspend fun cancel(partition: PartitionState) {
        withContext(Dispatchers.IO) {
            val state = _payload.value as? PayloadState.Ready ?: return@withContext
            LogManager.error("Cancelling ${partition.name} dumping...")
            state.buffer.put(partition.id * StructLayout.STRIDE + StructLayout.OFF_ABT, 1)
//            updatePartition(partition.id) { it.copy(status = PartStatus.FAILED) }
//            PayloadDumper.cancelPart(partition.id)
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
    object Idle : PayloadState()
    object Loading : PayloadState()
    data class Error(val message: String) : PayloadState()
    data class Ready(
        val name: String,
        val payloadType: PayloadType,
        val partitions: List<PartitionState>,
        val manifest: PartManifestOuterClass.PartManifest,
        val buffer: ByteBuffer
    ) : PayloadState()
}
data class PartitionState(val name: String, val size: Long, val incremental: Boolean, val hash: ByteString?, val downloadSize: Long?, val output: String?, val error: String?, val id: Int, val status: PartStatus = PartStatus.IDLE, val progress: Float = 0f)
enum class PartStatus(val code: Int) {
    IDLE(0), PENDING(1), RUNNING(2), COMPLETED(3), FAILED(4), VERIFYING(5), VERIFICATION_FAILED(6);

    companion object {
        fun fromCode(value: Int) = PartStatus.entries.find { it.code == value } ?: IDLE
    }

    fun isFinal() = when (this) {
        IDLE,
        COMPLETED,
        FAILED,
        VERIFICATION_FAILED -> true

        else -> false
    }
}
object StructLayout {
    const val STRIDE = 8
//    const val OFF_ID = 0
    const val OFF_ABT = 1
    const val OFF_STAT = 2
    const val OFF_PROG = 4
}