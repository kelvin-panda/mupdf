package com.xlk.example;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

/**
 * @author xlk
 * @date 2018/5/30
 */

public class WpsModel {
    /**
     * 打开文件的模式。
     */
    public static final String OPEN_MODE = "OpenMode";
    /**
     * 文件保存时是否发送广播
     */
    public static final String SEND_SAVE_BROAD = "SendSaveBroad";
    /**
     * 文件关闭时是否发送广播 =true  "cn.wps.moffice.broadcast.AfterClosed "
     */
    public static final String SEND_CLOSE_BROAD = "SendCloseBroad";
    /**
     * 监听home键并发广播
     */
    public static final String HOMEKEY_DOWN = "HomeKeyDown";
    /**
     * 监听back键并发广播
     */
    public static final String BACKKEY_DOWN = "BackKeyDown";
    /**
     * 第三方的包名，关闭的广播会包含该项。
     */
    public static final String THIRD_PACKAGE = "ThirdPackage";
    /**
     * 关闭文件时是否请空临时文件
     */
    public static final String CLEAR_BUFFER = "ClearBuffer";
    /**
     * 关闭文件时是否删除使用记录
     */
    public static final String CLEAR_TRACE = "ClearTrace";
    /**
     * 关闭文件时是否删除打开的文件
     */
    public static final String CLEAR_FILE = "ClearFile";
    /**
     * 文件上次查看的进度
     */
    public static final String VIEW_PROGRESS = "ViewProgress";
    /**
     * 是否自动跳转到上次查看的进度
     */
    public static final String AUTO_JUMP = "AutoJump";
    /**
     * 文件保存路径
     */
    public static final String SAVE_PATH = "SavePath";
    /**
     * 文件上次查看的视图的缩放
     */
    public static final String VIEW_SCALE = "ViewScale";
    /**
     * 文件上次查看的视图的X坐标
     */
    public static final String VIEW_SCALE_X = "ViewScrollX";
    /**
     * 文件上次查看的视图的Y坐标
     */
    public static final String VIEW_SCALE_Y = "ViewScrollY";
    /**
     * 批注的作者
     */
    public static final String USER_NAME = "UserName";
    /**
     * 以修订模式打开文档
     */
    public static final String ENTER_REVISE_MODE = "EnterReviseMode";
    /**
     * Wps生成的缓存文件外部是否可见
     */
    public static final String CACHE_FILE_INVISIBLE = "CacheFileInvisible";

    /**
     * 是否显示WPS界面
     */
    public static final String IS_SHOW_VIEW = "isShowView";

    //编辑
    public static final int INVALID_EDITPARAM = -1;
    public static final String AT_SAVE = "AT_SAVE";                   //保存
    public static final String AT_SAVEAS = "AT_SAVEAS";               //另存为
    public static final String AT_COPY = "AT_COPY";                   //复制
    public static final String AT_CUT = "AT_CUT";                      //剪切
    public static final String AT_PASTE = "AT_PASTE";                  //粘贴
    public static final String AT_SHARE = "AT_SHARE";                    //分享
    public static final String AT_PRINT = "AT_PRINT";                    //输出
    public static final String AT_SPELLCHECK = "AT_SPELLCHECK";          //拼写检查
    public static final String AT_QUICK_CLOSE_REVISEMODE = "AT_QUICK_CLOSE_REVISEMODE";          //快速关闭修订
    public static final String AT_MULTIDOCCHANGE = "AT_MULTIDOCCHANGE";          //多文档切换
    public static final String AT_EDIT_REVISION = "AT_EDIT_REVISION";
    public static final String AT_CURSOR_MODEL = "AT_CURSOR_MODEL";
    public static final String AT_PATH = "at_path";                             //编辑路径
    public static final String AT_CHANGE_COMMENT_USER = "AT_CHANGE_COMMENT_USER";
    public static final String AT_SHARE_PLAY = "AT_SHARE_PLAY";
    public static final String AT_GRID_BACKBOARD = "AT_GRID_BACKBOARD";
    public static final String SERIAL_NUMBER_OTHER = "SerialNumberOther"; // android 激活外部传入序列号

    @NonNull
    public static Bundle getBundle(Context context) {
        Bundle bundle = new Bundle();
        bundle.putString(WpsModel.OPEN_MODE, WpsModel.OpenMode.READ_MODE);
        bundle.putBoolean(WpsModel.ENTER_REVISE_MODE, false); // 以修订模式打开文档
        bundle.putBoolean(WpsModel.SEND_CLOSE_BROAD, true); // 文件关闭时是否发送广播
        bundle.putBoolean(WpsModel.SEND_SAVE_BROAD, true); // 文件保存时是否发送广播
        bundle.putBoolean(WpsModel.HOMEKEY_DOWN, true); // 单机home键是否发送广播
        bundle.putBoolean(WpsModel.BACKKEY_DOWN, true); // 单机back键是否发送广播
//            bundle.putBoolean("SendUnSaveBroad", true);
        bundle.putBoolean(WpsModel.IS_SHOW_VIEW, false); // 是否显示wps界面
        bundle.putBoolean(WpsModel.SAVE_PATH, true); // 文件这次保存的路径
        bundle.putBoolean(WpsModel.CACHE_FILE_INVISIBLE, false); // Wps生成的缓存文件外部是否可见
        bundle.putString(WpsModel.THIRD_PACKAGE, context.getPackageName()); // 第三方应用的包名，用于对改应用合法性的验证
        bundle.putBoolean(WpsModel.CLEAR_TRACE, true);// 清除打开记录
        bundle.putBoolean(WpsModel.CLEAR_BUFFER, true);// 关闭文件时是否请空临时文件
        bundle.putBoolean(WpsModel.CLEAR_FILE, true); //关闭后删除打开文件

        bundle.putBoolean("AutoSave", false); //关闭自动保存
        bundle.putBoolean("NoCopy", true);
        return bundle;
    }

    public class OpenMode {
        public static final String NORMAL = "Normal";// 正常模式
        public static final String READ_ONLY = "ReadOnly";// 只读模式
        public static final String READ_MODE = "ReadMode";// 打开直接进入阅读器模式
        // 仅Word、TXT文档支持
        public static final String SAVE_ONLY = "SaveOnly";// 保存模式(打开文件,另存,关闭)
        // 仅Word、TXT文档支持
    }

    public class ClassName {
//        public static final String NORMAL = "cn.wps.moffice.main.StartPublicActivity";
        public static final String NORMAL = "cn.wps.moffice.documentmanager.PreStartActivity2";// 普通版
        public static final String ENGLISH = "cn.wps.moffice.documentmanager.PreStartActivity2";// 英文版
        public static final String ENTERPRISE = "cn.wps.moffice.documentmanager.PreStartActivity2";// 企业版
    }

    public class PackageName {
//        public static final String NORMAL = "cn.wps.moffice_i18n";// 普通版
        public static final String NORMAL = "cn.wps.moffice_eng";// 普通版
        public static final String ENGLISH = "cn.wps.moffice_eng";// 英文版
        public static final String ENTERPRISE = "cn.wps.moffice_ent";// 企业版
        // https://zhuanlan.zhihu.com/p/156628797
        public static final String ENTERPRISE_PRO = "com.kingsoft.moffice_pro";// 企业版
    }

    public class Reciver {
        public static final String ACTION_BACK = "com.kingsoft.writer.back.key.down";// 返回键广播
        public static final String ACTION_HOME = "com.kingsoft.writer.home.key.down";// Home键广播
        public static final String ACTION_SAVE = "cn.wps.moffice.file.save";// 保存广播
        /**
         * <p>广播包含信息：</p>
         * <table cellspacing=8 cellpadding=5>
         *     <tr>
         *     <th align=left>参数名
         *     <th align=left>参数说明
         *     <th align=left>类型
         *     <th align=left>默认值
         *     </tr>
         *
         *     <tr>
         *     <th align=left>CloseFile
         *     <th align=left>关闭文件的路径
         *     <th align=left>String
         *     <th align=left>
         *     </tr>
         *
         *     <tr>
         *     <th align=left>ThirdPackage
         *     <th align=left>传入的第三方的包名
         *     <th align=left>String
         *     <th align=left>
         *     </tr>
         */
        public static final String ACTION_CLOSE = "cn.wps.moffice.file.close";// 关闭文件广播

        /**
         * <p>广播包含信息：</p>
         * <table cellspacing=8 cellpadding=5>
         *     <tr>
         *     <th align=left>参数名
         *     <th align=left>参数说明
         *     <th align=left>类型
         *     <th align=left>默认值
         *     </tr>
         *
         *     <tr>
         *     <th align=left>CurrentPath
         *     <th align=left>当前文档路径
         *     <th align=left>String
         *     <th align=left>
         *     </tr>
         *
         *     <tr>
         *     <th align=left>ThirdPartyPackage
         *     <th align=left>传入的第三方的包名
         *     <th align=left>String
         *     <th align=left>
         *     </tr>
         *
         *     <tr>
         *     <th align=left>SaveAs
         *     <th align=left>本次保存事件是否是另存
         *     <th align=left>Boolean
         *     <th align=left>
         *     </tr>
         */
        public static final String ACTION_AFTER_SAVE = "cn.wps.moffice.broadcast.AfterSaved";// 关闭文件广播
        /**
         * <p>广播包含信息：</p>
         * <table cellspacing=8 cellpadding=5>
         *     <tr>
         *     <th align=left>参数名
         *     <th align=left>参数说明
         *     <th align=left>类型
         *     <th align=left>默认值
         *     </tr>
         *
         *     <tr>
         *     <th align=left>CurrentPath
         *     <th align=left>关闭文件的路径
         *     <th align=left>String
         *     <th align=left>
         *     </tr>
         *
         *     <tr>
         *     <th align=left>ThirdPartyPackage
         *     <th align=left>传入的第三方的包名
         *     <th align=left>String
         *     <th align=left>
         *     </tr>
         *
         *     <tr>
         *     <th align=left>ViewProgress
         *     <th align=left>文件查看的进度
         *     <th align=left>float
         *     <th align=left>0.00%
         *     </tr>
         *
         *     <tr>
         *     <th align=left>ViewScale
         *     <th align=left>文件上次查看的视图的缩放
         *     <th align=left>float
         *     <th align=left>1
         *     </tr>
         *
         *     <tr>
         *     <th align=left>ViewScrollX
         *     <th align=left>文件上次查看的视图的X坐标
         *     <th align=left>int
         *     <th align=left>0
         *     </tr>
         *
         *     <tr>
         *     <th align=left>ViewScrollY
         *     <th align=left>文件上次查看的视图的Y坐标
         *     <th align=left>int
         *     <th align=left>0
         *     </tr>
         */
        public static final String ACTION_AFTER_CLOSE = "cn.wps.moffice.broadcast.AfterClosed";// 关闭文件广播
    }

    public class ReciverExtra {
        //关闭文件广播返回的信息
        public static final String CLOSEFILE = "CloseFile";//关闭文件的路径
        public static final String THIRDPACKAGE = "ThirdPackage";//传入的第三方的包名
        public static final String VIEWPROGRESS = "ViewProgress";//文件查看的进度
        public static final String VIEWSCALE = "ViewScale";//文件上次查看的视图的缩放
        public static final String VIEWSCROLLX = "ViewScrollX";//文件上次查看的视图的X坐标
        public static final String VIEWSCROLLY = "ViewScrollY";//文件上次查看的视图的Y坐标
        //保存文件广播返回的信息
        public static final String OPENFILE = "OpenFile";//文件最初的路径
        public static final String SAVEPATH = "SavePath";//文件这次保存的路径
    }
}
