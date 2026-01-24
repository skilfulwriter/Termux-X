package com.termux.zerocore.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Message
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.ub.util.custom.dialog.BaseDialogDown
import com.blockchain.ub.utils.httputils.BaseHttpUtils
import com.blockchain.ub.utils.httputils.HttpResponseListenerBase
import com.example.xh_lib.utils.UUtils
import com.google.gson.Gson
import com.lzy.okgo.model.Response
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.shared.logger.Logger
import com.termux.zerocore.bean.online_sh.Data
import com.termux.zerocore.bean.online_sh.OnLineShBean
import com.termux.zerocore.data.DownLoadShEntey
import com.termux.zerocore.http.HTTPIP
import com.termux.zerocore.url.FileUrl

class OnLineShDialog : BaseDialogDown {
    private var recycler_view:RecyclerView? = null
    private var loading_data:LinearLayout? = null
    private var show_data: RelativeLayout? = null
    private var service_name: TextView? = null
    private var mOnItemClickListener:OnItemClickListener? = null
    private val LOG_TAG = "Termux--Apk:OnLineShDialog"
    constructor(context: Context) : super(context)
    constructor(context: Context, themeResId: Int) : super(context, themeResId)


    override fun initViewDialog(mView: View) {
        recycler_view = mView.findViewById(R.id.recycler_view)
        loading_data = mView.findViewById(R.id.loading_data)
        show_data = mView.findViewById(R.id.show_data)
        service_name = mView.findViewById(R.id.service_name)
        val uploadScript = mView.findViewById<TextView>(R.id.upload_script)

        service_name?.let {
            it.paint.flags = Paint.UNDERLINE_TEXT_FLAG
            it.setOnClickListener {
                UUtils.startUrl("https://down.xheishou.top/down/在线脚本")
            }
        }

        uploadScript?.let {
            it.paint.flags = Paint.UNDERLINE_TEXT_FLAG
            it.setOnClickListener {
                UUtils.startUrl("https://down.xheishou.top/down/在线脚本")
            }
        }

        downloadHttpData()
    }

    //下载数据
    private fun downloadHttpData(){
        Logger.logDebug(LOG_TAG, "downloadHttpData start on url: ${HTTPIP.ONLINE_SH_JSON}")
        BaseHttpUtils().getUrl(HTTPIP.ONLINE_SH_JSON,object :HttpResponseListenerBase{
            override fun onSuccessful(msg: Message, mWhat: Int) {

                try {
                    val fromJson =
                        Gson().fromJson<OnLineShBean>(msg.obj as String, OnLineShBean::class.java)
                    show_data?.visibility = View.VISIBLE
                    loading_data?.visibility = View.GONE
                    recycler_view?.layoutManager = LinearLayoutManager(this@OnLineShDialog.mContext)
                    service_name?.text = fromJson.serviceName
                    val data = fromJson.data
                    val onLineShDialogAdapter = OnLineShDialogAdapter(data,fromJson.ip)
                    recycler_view?.adapter = onLineShDialogAdapter

                    onLineShDialogAdapter.setAdapterOnStartItemClickListener(object :OnLineShDialogAdapter.AdapterOnStartItemClickListener{
                        override fun click(msg: String) {
                            show_data?.visibility = View.GONE
                            loading_data?.visibility = View.VISIBLE
                        }

                    })

                    onLineShDialogAdapter.setAdapterOnItemClickListener(object :OnLineShDialogAdapter.AdapterOnItemClickListener{
                        override fun click(msg: String, mode: String) {
                            mOnItemClickListener?.click(msg, mode)
                        }

                    })


                }catch (e:Exception){
                    e.printStackTrace()
                    UUtils.runOnUIThread {
                        UUtils.showMsg(UUtils.getString(R.string.服务器在线但数据格式出错))
                        dismiss()
                    }
                }




            }

            override fun onFailure(response: Response<String>?, msg: String, mWhat: Int) {

             UUtils.runOnUIThread {
                 UUtils.showMsg(UUtils.getString(R.string.服务器已离线))
                 dismiss()
             }
            }

        }, HashMap(),558)


    }

    override fun getContentView(): Int {
        return R.layout.dialog_on_line_sh
    }

    class OnLineShDialogAdapter : RecyclerView.Adapter<OnLineShDialogViewHolder>{

        private var data: List<Data>? = null
        private var ip: String = ""
        private val LOG_TAG = "Termux--Apk:OnLineShDialogAdapter"
        private var mAdapterOnItemClickListener: AdapterOnItemClickListener? = null
        private var mAdapterOnStartItemClickListener:AdapterOnStartItemClickListener? = null
        constructor(data: List<Data>,ip:String) : super(){
            this.data = data
            this.ip = ip
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): OnLineShDialogViewHolder {

            return OnLineShDialogViewHolder(UUtils.getViewLayViewGroup(R.layout.item_on_line_sh,parent))
        }

        override fun onBindViewHolder(holder: OnLineShDialogViewHolder, position: Int) {
            val data1 = data!![position]
            if(data1.isDownload == "1"){
                holder.show_img_jz?.visibility = View.VISIBLE
            }else{
                holder.show_img_jz?.visibility = View.GONE
            }

            holder.name?.setText(data1.name)
            holder.size?.text = "${UUtils.getString(R.string.大小)}:1KB~500KB"

            val noteText = data1.note
            if (!noteText.isNullOrEmpty()) {
                val spannableString = SpannableString(noteText)
                val urlRegex = Regex("https?://\\S+")
                val match = urlRegex.find(noteText)
                if (match != null) {
                    spannableString.setSpan(ForegroundColorSpan(Color.RED), match.range.first, match.range.last + 1, 0)
                }
                holder.note?.text = spannableString
            } else {
                holder.note?.text = ""
            }

            holder.note?.setOnClickListener {
                if (!noteText.isNullOrEmpty()) {
                    val urlRegex = Regex("https?://\\S+")
                    val match = urlRegex.find(noteText)
                    if (match != null) {
                        UUtils.startUrl(match.value)
                    }
                }
            }
            holder.download?.setOnClickListener {

                if(data1.isDownload == "1"){
                    UUtils.showMsg(UUtils.getString(R.string.当前文件下载已被禁止))
                    return@setOnClickListener
                }
                try {
                    Logger.logDebug(LOG_TAG, "file path: ${FileUrl.mainHomeUrl}/${data1.download.split("/")[data1.download.split("/").size - 1]}")
                    DownLoadShEntey.downLoadSh("$ip${data1.download}","${FileUrl.mainHomeUrl}/${data1.download.split("/")[data1.download.split("/").size - 1]}")
                    mAdapterOnStartItemClickListener?.click("")
                    DownLoadShEntey.setDownLoadShEnteyListener(object :DownLoadShEntey.DownLoadShEnteyListener{
                        override fun downLoadFile(filePath: String) {
                            val fileName = data1.download.split("/")[data1.download.split("/").size - 1]
                            var installCmd = ""
                            
                            // 检测 Python 依赖
                            if (fileName.endsWith(".py", ignoreCase = true)) {
                                val dependencies = detectPythonDependencies(filePath)
                                // 检查 pip 是否安装，如果未安装则先安装 pip
                                // 为了确保脚本能正常运行，我们在安装依赖之前先检查并安装 pip
                                var checkPipCmd = "if ! command -v pip > /dev/null; then pkg install python-pip -y; fi && "
                                
                                if (dependencies.isNotEmpty()) {
                                    // 过滤已安装的库 (这里简化处理，直接尝试安装)
                                    // 为了加快速度，最好只安装未安装的。但在 Shell 中检查比较麻烦
                                    // 我们可以使用 pip install package1 package2 ...
                                    installCmd = "${checkPipCmd}pip install ${dependencies.joinToString(" ")} && "
                                } else {
                                    // 即使没有依赖，如果是 Python 脚本，最好也确保环境就绪（虽然通常有 python 就行，但用户可能需要 pip）
                                    // 不过如果没有额外依赖，强制检查 pip 可能会拖慢速度，视情况而定
                                    // 这里我们只在有依赖需要安装时强制检查 pip，或者如果用户反馈需要
                                    installCmd = "" 
                                    // 如果用户遇到 "pip is not installed" 错误，说明他们尝试运行的脚本可能隐式需要 pip 或者我们之前的逻辑有问题
                                    // 如果脚本本身没有 import 第三方库，但环境缺 pip，运行脚本本身通常不需要 pip
                                    // 除非脚本内部调用了 pip。
                                    
                                    // 鉴于用户反馈的问题，即使没有检测到依赖，如果脚本运行出错是因为缺 pip，那体验不好。
                                    // 但如果脚本不需要 pip，预装 pip 浪费时间。
                                    // 让我们修改逻辑：如果是 py 文件，无论是否有依赖，都尝试确保 pip 存在？
                                    // 不，只有在需要安装依赖时，pip 才是必须的。
                                    // 用户的报错是因为 "The program pip is not installed" 是在执行 "pip install ..." 时出现的。
                                    // 所以只要我们在执行 pip install 之前确保安装了 pip 即可。
                                }
                            }
                            
                            val runCmd = if (fileName.endsWith(".py", ignoreCase = true)) {
                                "python $fileName"
                            } else {
                                "./$fileName"
                            }
                            // 检查运行模式: "1"/"true" -> Kali, "su" -> Termux Root, 其他 -> Termux User
                            val mode = data1.kali ?: "0"
                            
                            mAdapterOnItemClickListener?.click("cd ~ && ${installCmd}chmod 777 $fileName && $runCmd", mode)
                        }

                        override fun error(msg: String) {
                            UUtils.showMsg(msg)
                        }

                    })

                }catch (e:Exception){
                    UUtils.showMsg(UUtils.getString(R.string.当前文件下载已被禁止))
                }

            }

        }
        
        private fun detectPythonDependencies(filePath: String): List<String> {
            val dependencies = mutableSetOf<String>()
            val file = java.io.File(filePath)
            if (!file.exists()) return emptyList()

            // 常见的标准库，不需要安装
            val stdLib = setOf(
                "os", "sys", "time", "random", "re", "json", "threading", "subprocess", "socket", "struct", "math",
                "datetime", "base64", "hashlib", "urllib", "platform", "argparse", "collections", "itertools",
                "functools", "logging", "traceback", "types", "io", "shutil", "glob", "pickle", "copy", "warnings",
                "string", "uuid", "binascii", "hmac", "concurrent", "contextlib", "abc", "enum", "pathlib",
                "decimal", "fractions", "statistics", "unicodedata", "getpass", "optparse", "queue", "asyncio",
                "typing", "dataclasses", "inspect", "pkgutil", "importlib", "ssl", "sqlite3", "ctypes", "email",
                "xml", "html", "http", "zlib", "gzip", "bz2", "lzma", "zipfile", "tarfile", "csv", "calendar",
                "multiprocessing", "weakref", "heapq", "bisect", "array", "sets", "sched", "mutex", "queue",
                "tokenize", "keyword", "token", "symbol", "py_compile", "compileall", "dis", "pydoc", "doctest",
                "unittest", "test", "bdb", "pdb", "profile", "cProfile", "timeit", "trace", "distutils", "ensurepip",
                "venv", "curses", "textwrap", "locale", "gettext"
            )
            
            // 常用第三方库映射 (Module Name -> Package Name)
            val mapping = mapOf(
                "Crypto" to "pycryptodome",
                "cv2" to "opencv-python",
                "bs4" to "beautifulsoup4",
                "PIL" to "Pillow",
                "yaml" to "PyYAML",
                "sklearn" to "scikit-learn",
                "telegram" to "python-telegram-bot",
                "dns" to "dnspython",
                "requests" to "requests",
                "numpy" to "numpy",
                "matplotlib" to "matplotlib",
                "pandas" to "pandas",
                "scapy" to "scapy",
                "colorama" to "colorama",
                "mechanize" to "mechanize",
                "telethon" to "telethon"
            )

            try {
                file.forEachLine { line ->
                    val trimLine = line.trim()
                    if (trimLine.startsWith("import ") || trimLine.startsWith("from ")) {
                        // 简单的正则匹配提取模块名
                        val parts = trimLine.split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            var module = parts[1].split(".")[0]
                            // 清理模块名 (如 import os, sys 中的逗号)
                            module = module.replace(",", "")
                            
                            if (module.isNotEmpty() && !stdLib.contains(module)) {
                                 // 如果在映射表中，使用映射后的包名；否则尝试直接安装模块名
                                 val pkg = mapping[module] ?: module
                                 dependencies.add(pkg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return dependencies.toList()
        }

        override fun getItemCount(): Int {

            return data!!.size
        }

        public fun setAdapterOnItemClickListener(mAdapterOnItemClickListener:AdapterOnItemClickListener){
            this.mAdapterOnItemClickListener = mAdapterOnItemClickListener
        }
        public fun setAdapterOnStartItemClickListener(mAdapterOnStartItemClickListener:AdapterOnStartItemClickListener){
            this.mAdapterOnStartItemClickListener = mAdapterOnStartItemClickListener
        }
        public interface AdapterOnItemClickListener{

            fun click(msg:String, mode: String)

        }

        public interface AdapterOnStartItemClickListener{

            fun click(msg:String)

        }

    }

    class OnLineShDialogViewHolder : RecyclerView.ViewHolder{

        public var name:TextView? = null
        public var size:TextView? = null
        public var note:TextView? = null
        public var show_img_jz:ImageView? = null
        public var download:ImageView? = null

        constructor(itemView: View) : super(itemView){
            name = itemView.findViewById(R.id.name)
            size = itemView.findViewById(R.id.size)
            note = itemView.findViewById(R.id.note)
            show_img_jz = itemView.findViewById(R.id.show_img_jz)
            download = itemView.findViewById(R.id.download)
        }
    }

    public fun setOnItemClickListener(mOnItemClickListener:OnItemClickListener){
        this.mOnItemClickListener = mOnItemClickListener
    }

    public interface OnItemClickListener{
        fun click(msg:String, mode: String)
    }

}
