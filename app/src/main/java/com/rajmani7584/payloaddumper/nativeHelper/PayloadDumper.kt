package com.rajmani7584.payloaddumper.nativeHelper

import com.rajmani7584.payloaddumper.engine.chromeos_update_engine.UpdateMetadata
import com.rajmani7584.payloaddumper.engine.part_manifest.PartManifestOuterClass
import com.rajmani7584.payloaddumper.model.PayloadType
import java.nio.ByteBuffer

object PayloadDumper {

    private external fun initSession(): Int
    private external fun openPayload(pType: Int, path: String, concurrency: Int): Int
    private external fun fetchHeader(): String
    private external fun fetchPartManifest(): ByteArray
    private external fun fetchSignatures(): ByteArray
    private external fun bindBuffer(taskCount: Int, address: ByteBuffer): Int
    private external fun dump(id: Int, out: String): Int
    private external fun fetchDumpError(id: Int): String
    private external fun cancelDump(id: Int): Int

    init {
        System.loadLibrary("payload_dumper")
    }

    fun init(): Result<Int> {
        try {
            val i = initSession()
            return Result.success(i)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun open(payloadType: PayloadType): Result<Int> {
        try {
            val i = openPayload(payloadType.getTypeInt(), payloadType.getPathString(), 4)
            return Result.success(i)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getHeader(): Result<String> {
        try {
            val h = fetchHeader()
            return Result.success(h)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getManifest(): Result<PartManifestOuterClass.PartManifest> {
        try {
            val b = fetchPartManifest()
            val manifest = PartManifestOuterClass.PartManifest.parseFrom(b)

            return Result.success(manifest)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getSignatures(): Result<UpdateMetadata.Signatures> {
        try {
            val b = fetchSignatures()
            val manifest = UpdateMetadata.Signatures.parseFrom(b)

            return Result.success(manifest)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    fun setupBuffer(taskCount: Int, address: ByteBuffer): Result<Int> {
        try {
            val b = bindBuffer(taskCount, address)

            return Result.success(b)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    fun dumpPart(id: Int, out: String): Result<Int> {
        try {
            val b = dump(id, out)

            return Result.success(b)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getDumpError(id: Int): Result<String> {
        try {
            val b = fetchDumpError(id)
            return Result.success(b)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun cancelPart(id: Int): Result<Int> {
        try {
            val b = cancelDump(id)
            return Result.success(b)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}