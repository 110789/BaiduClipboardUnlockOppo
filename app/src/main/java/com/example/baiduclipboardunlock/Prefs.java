package com.example.baiduclipboardunlock;

public class Prefs {
    public static final String MODULE_PACKAGE = "com.example.baiduclipboardunlock";
    public static final String PREFS_NAME = "clipboard_unlock_settings";

    // 单条剪贴板内容字符长度上限 (对应 com.baidu.xn1#r 内部原本硬编码的 7000)
    //
    // 注意: 复制事件会先打包成 Intent 经 startService() 跨进程发给
    // RecordService 落库, 这一步会走 Binder, 单次传输有约 1MB 的系统硬限制。
    // 设得太大 (比如几十万字符) 会导致复制"看起来成功", 但历史列表里
    // 根本不会出现这条记录 (Binder 传输失败, 没有任何报错)。
    // 默认值保守设置, 需要更大就自行加大并实测边界。
    public static final String KEY_WORD_LIMIT = "word_limit";
    public static final int DEFAULT_WORD_LIMIT = 50000;

    // MD5 去重绕过开关 (复制重复内容时是否仍保留为独立历史记录)
    public static final String KEY_ENABLE_DEDUP_BYPASS = "enable_dedup_bypass";
    public static final boolean DEFAULT_ENABLE_DEDUP_BYPASS = true;

    // 自定义剪贴板历史保留条数 (对应 com.baidu.ko1#a() 读取到的数量上限)
    public static final String KEY_ENABLE_COUNT_OVERRIDE = "enable_count_override";
    public static final boolean DEFAULT_ENABLE_COUNT_OVERRIDE = false;

    public static final String KEY_MAX_COUNT = "max_count";
    public static final int DEFAULT_MAX_COUNT = 200;

    // 历史列表显示过滤绕过 (对应 com.baidu.qo1$l#o 里超过 14000 字符
    // 就从待显示列表 remove() 掉的逻辑; 内容其实已经存进数据库了,
    // 只是被这一步从"要显示的列表"里剔除, 导致面板看不到)
    public static final String KEY_ENABLE_HISTORY_FILTER_BYPASS = "enable_history_filter_bypass";
    public static final boolean DEFAULT_ENABLE_HISTORY_FILTER_BYPASS = true;

    public static final int MAX_LIMIT_VALUE = 100_000_000;
    public static final int MAX_COUNT_LIMIT_VALUE = 100_000;
}
