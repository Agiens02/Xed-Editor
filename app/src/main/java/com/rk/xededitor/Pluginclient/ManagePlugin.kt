package com.rk.xededitor.Pluginclient

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libPlugin.server.Server
import com.rk.libPlugin.server.Server.Companion.indexPlugins
import com.rk.xededitor.databinding.ActivityManageBinding
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Objects
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream



const val PICK_FILE_REQUEST_CODE = 37579

class ManagePlugin : AppCompatActivity() {
    lateinit var binding: ActivityManageBinding
    lateinit var madapter:CustomListAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBinding.inflate(layoutInflater)

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        Objects.requireNonNull(supportActionBar)?.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.title = "Manage Plugins"

        setContentView(binding.root)

        application.indexPlugins()
        madapter = CustomListAdapter(this, Server.getInstalledPlugins())
        binding.listView.adapter = madapter

        binding.fab.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Add Plugin")
                .setMessage("Choose the plugin zip file from storage to install it.")
                .setNegativeButton("Cancel", null).setPositiveButton("Choose") { dialog, which ->
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "*/*"
                    startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
                }.show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == PICK_FILE_REQUEST_CODE) {
            data?.data?.let { uri ->
                val fileName = getFileName(uri).toString()
                if (fileName.endsWith(".zip").not()) {
                    rkUtils.toast(this, "Invalid file type, zip file expected")
                    return
                }

                val tempDir = File(cacheDir, "temp$fileName")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }

                val pluginsDir = File(filesDir.parentFile!!, "plugins")
                if (pluginsDir.exists().not()) {
                    pluginsDir.mkdirs()
                }

                extractZip(uri, tempDir)

                var isInstalled = false
                tempDir.listFiles()?.forEach { f ->
                    if (f.isDirectory && File(f, "manifest.json").exists()) {
                        copyDirectory(f.parentFile!!, pluginsDir)
                        isInstalled = true
                    }
                }

                lifecycleScope.launch(Dispatchers.IO){
                    tempDir.deleteRecursively()
                }

                if (isInstalled){
                    rkUtils.toast(this, "Installed Successfully")
                    recreate()
                }else{
                    rkUtils.toast(this, "Failed to install")
                }




            }
        }


    }

    private fun copyDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists()) return
        if (!targetDir.exists()) targetDir.mkdirs()

        sourceDir.listFiles()?.forEach { file ->
            val newFile = File(targetDir, file.name)
            if (file.isDirectory) {
                copyDirectory(file, newFile)
            } else {
                try {
                    Files.copy(file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (e: IOException) {
                    e.printStackTrace()
                    rkUtils.toast(this, e.message)
                }
            }
        }
    }

    private fun extractZip(zipUri: Uri, file: File) {
        contentResolver.openInputStream(zipUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipInputStream ->
                var entry: ZipEntry? = zipInputStream.nextEntry
                while (entry != null) {
                    val file = File(file, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { outputStream ->
                            val buffer = ByteArray(1024)
                            var length: Int
                            while (zipInputStream.read(buffer).also { length = it } > 0) {
                                outputStream.write(buffer, 0, length)
                            }
                        }
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                result = if (nameIndex != -1) {
                    it.getString(nameIndex)
                } else {
                    null
                }
            }
        }
        return result
    }

}