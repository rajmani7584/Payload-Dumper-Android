package com.rajmani7584.payloaddumper.models

import java.io.File

object PayloadDumper {
    private external fun getPartitionList(path: String): String
    private external fun getRawData(path: String): String
    private external fun extractPartition(
        path: String,
        partition: String,
        outputPath: String,
        onCallback: RustCallback
    ): String

    init {
        System.loadLibrary("payload_dumper_rust")
    }

    fun getPartitions(viewModel: DataViewModel, path: String): Result<Payload> {
        if (viewModel.hasPermission.value == false) return Result.failure(Error("Error: Permission not granted"))
        if (!File(path).exists()) return Result.failure(Error("Error: File not found or maybe deleted"))
        val result = getPartitionList(path)
        return if (result.startsWith("Error")) {
            Result.failure(Error(result))
        } else {
            val payloadData = result.removePrefix("data:").split(":")
            if (payloadData.size != 3) return Result.failure(Error("Error: Data from rust is Corrupted!"))
            val manifestData = payloadData[0].split("|")
            if (manifestData.size != 2) return Result.failure(Error("Error: Manifest data from rust is Corrupted!"))
            var largest = ""
            val partitions = payloadData[1].split(",").map { partition ->
                val parts = partition.split("|")
                if (largest.length < parts[0].length) largest = parts[0]
                Partition(parts[0], parts[1].toLongOrNull() ?: 0L, parts[2])
            }
            val incremental = payloadData[2] == "true"
            if (partitions.isEmpty()) return Result.failure(Error("Error: No Partition Found"))

            Result.success(Payload(path = path, name = path.split("/").last(), version = manifestData.first(), patch = manifestData.last().let { it.ifEmpty { "Unknown" } }, partitions = partitions, incremental = incremental, largest = largest))
        }
    }

    fun getRaw(viewModel: DataViewModel, path: String): String {
        if (viewModel.hasPermission.value == false) return "Error: Permission Denied"
        if (!File(path).exists()) return "Error: File not found or maybe deleted"
        val result = getRawData(path)
        return result
    }

    fun extract(path: String, partition: String, outputPath: String, onProgress: (Long) -> Unit, onVerify: (Int) -> Unit): Result<String> {
        File(outputPath).parentFile?.apply { if (!exists()) mkdirs() }
        val result = extractPartition(path, partition, outputPath, OnRustCallback(onProgress, onVerify))
        return if (result.startsWith("Done")) {
            Result.success("Done")
        } else if (result.startsWith("Error:")) {
            Result.failure(Exception(result))
        } else {
            Result.failure(Exception("Error: Can't extract, error: Unknown"))
        }
    }

}
interface RustCallback {
    fun onProgressCallback(progress: Long)
    fun onVerifyCallback(status: Int)
}

class OnRustCallback(private val onProgress: (Long) -> Unit, private val onVerify: (Int) -> Unit): RustCallback {
    override fun onProgressCallback(progress: Long) {
        onProgress(progress)
    }

    override fun onVerifyCallback(status: Int) {
        onVerify(status)
    }
}
data class Payload(val path: String, val name: String, val version: String, val patch: String, val partitions: List<Partition>, val incremental: Boolean, val largest: String)
data class Partition(val name: String, val size: Long, val hash: String)