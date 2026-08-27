package com.example.baiduclipboardunlock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 百度输入法 OPPO 定制版 (com.baidu.input_oppo) 剪贴板限制解除。
 *
 * 百度每次更新都会重新混淆类名/方法名, 所以除了长度限制(改用 hook
 * Android 系统自带的 InputFilter$LengthFilter, 不依赖百度自己的类名,
 * 永远不会失效)之外, 其余三处都保留"多候选名单"逐个尝试的兜底写法:
 * 装完模块后如果某个功能不生效, 大概率是百度又重新混淆了, 需要重新
 * 反编译当前版本, 把新的类名/方法名加进对应的候选数组里。
 *
 *  1. android.text.InputFilter$LengthFilter#<init>(int) —— 系统API,
 *     当传入的 max 正好是百度原本写死的 7000 时, 替换成自定义长度。
 *  2. com.baidu.input.ime.front.clipboard.Record 的 md5 getter ——
 *     决定复制内容是否被判定为"重复"。候选方法名: e/E/t/P。
 *  3. 剪贴板保留条数上限的 getter —— 候选 (类名,方法名):
 *     (yu.a, f) / (com.baidu.ko1, a)。
 *  4. 历史列表按 14000 字符过滤/移除记录的方法 —— 候选 (类名,方法名):
 *     (xu.w, setDatas) / (com.baidu.qo1$l, o)。
 */
public class BaiduHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.baidu.input_oppo";

    private static final int ORIGINAL_LENGTH_LIMIT = 7000;

    private static final String[] MD5_GETTER_CANDIDATES = {"e", "E", "t", "P"};

    // {类名, 方法名} 候选列表, 新版本排在前面优先尝试
    private static final String[][] MAX_COUNT_CANDIDATES = {
            {"yu.a", "f"},
            {"com.baidu.ko1", "a"},
    };

    // {类名, 方法名, 承载最终显示数据的字段名} —— 字段名是反编译时直接
    // 在方法体里确认过的精确值, 不再用"猜第一个List类型字段"这种不可靠
    // 方式, 避免类里有多个List字段时误伤到不相关的那个。
    // 字段名留空("") 表示这个候选未知具体字段名, 会退化为遍历查找
    // (仅作为老版本 build 的兜底, 找不到就放弃, 不强行处理)。
    private static final String[][] HISTORY_FILTER_CANDIDATES = {
            {"xu.w", "setDatas", "f"},
            {"com.baidu.qo1$l", "o", ""},
    };

    private XSharedPreferences prefs;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        prefs = new XSharedPreferences(Prefs.MODULE_PACKAGE, Prefs.PREFS_NAME);
        prefs.makeWorldReadable();

        ClassLoader cl = lpparam.classLoader;

        try {
            hookLengthLimit();
        } catch (Throwable ignored) {
        }

        try {
            hookDedupBypass(cl);
        } catch (Throwable ignored) {
        }

        try {
            hookMaxCount(cl);
        } catch (Throwable ignored) {
        }

        try {
            hookHistoryLengthFilter(cl);
        } catch (Throwable ignored) {
        }
    }

    private int wordLimit() {
        prefs.reload();
        return prefs.getInt(Prefs.KEY_WORD_LIMIT, Prefs.DEFAULT_WORD_LIMIT);
    }

    private boolean dedupBypassEnabled() {
        prefs.reload();
        return prefs.getBoolean(Prefs.KEY_ENABLE_DEDUP_BYPASS, Prefs.DEFAULT_ENABLE_DEDUP_BYPASS);
    }

    private boolean countOverrideEnabled() {
        prefs.reload();
        return prefs.getBoolean(Prefs.KEY_ENABLE_COUNT_OVERRIDE, Prefs.DEFAULT_ENABLE_COUNT_OVERRIDE);
    }

    private int maxCount() {
        prefs.reload();
        return prefs.getInt(Prefs.KEY_MAX_COUNT, Prefs.DEFAULT_MAX_COUNT);
    }

    private boolean historyFilterBypassEnabled() {
        prefs.reload();
        return prefs.getBoolean(Prefs.KEY_ENABLE_HISTORY_FILTER_BYPASS,
                Prefs.DEFAULT_ENABLE_HISTORY_FILTER_BYPASS);
    }

    // ---------------------------------------------------------------
    // 1. 单条内容字符长度限制: hook 系统自带的 InputFilter$LengthFilter
    //    构造函数, 不依赖百度自己的任何类名, 版本更新也不会失效。
    //    只在 max 正好等于百度原本写死的 7000 时才介入, 避免误伤应用
    //    里其他跟剪贴板无关、也用了 LengthFilter 的输入框。
    // ---------------------------------------------------------------
    private void hookLengthLimit() throws Throwable {
        XposedHelpers.findAndHookConstructor(android.text.InputFilter.LengthFilter.class,
                int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object arg = param.args[0];
                if (arg instanceof Integer && (Integer) arg == ORIGINAL_LENGTH_LIMIT) {
                    param.args[0] = wordLimit();
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // 2. MD5 去重绕过: com.baidu.input.ime.front.clipboard.Record 的
    //    md5 getter。只对"无参数 + 返回值是 CharSequence/String 类型"的
    //    候选方法生效, 避免命中同名但用途完全不同的方法。
    // ---------------------------------------------------------------
    private void hookDedupBypass(ClassLoader cl) throws Throwable {
        Class<?> record = XposedHelpers.findClass(
                "com.baidu.input.ime.front.clipboard.Record", cl);

        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (dedupBypassEnabled()) {
                    param.setResult("empty_md5_" + System.nanoTime());
                }
            }
        };

        for (String name : MD5_GETTER_CANDIDATES) {
            try {
                Method m = record.getDeclaredMethod(name);
                if (Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                Class<?> returnType = m.getReturnType();
                if (!CharSequence.class.isAssignableFrom(returnType)) {
                    continue;
                }
                XposedBridge.hookMethod(m, hook);
            } catch (NoSuchMethodException ignored) {
            }
        }
    }

    // ---------------------------------------------------------------
    // 3. 自定义剪贴板保留条数
    // ---------------------------------------------------------------
    private void hookMaxCount(ClassLoader cl) throws Throwable {
        for (String[] candidate : MAX_COUNT_CANDIDATES) {
            try {
                Class<?> cls = XposedHelpers.findClass(candidate[0], cl);
                Method m = cls.getDeclaredMethod(candidate[1]);
                if (m.getReturnType() != int.class) {
                    continue;
                }
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (countOverrideEnabled()) {
                            param.setResult(maxCount());
                        }
                    }
                });
            } catch (Throwable ignored) {
                // 这个候选在当前 build 里不存在, 试下一个
            }
        }
    }

    // ---------------------------------------------------------------
    // 4. 历史列表显示过滤(按 14000 字符移除记录)绕过
    //
    // 注意: 这个方法在不同 build 里职责可能不一样 —— 有的版本这个方法
    // 只单纯做"过滤", 有的版本(比如 xu.w#setDatas)在过滤之后紧接着
    // 还会把处理结果赋值给界面用来渲染(list.clear()+addAll())。
    // 如果简单粗暴地跳过整个方法, 会连"赋值显示"这一步也一起跳过,
    // 导致列表完全空白(不管长短都不显示), 而不只是超长的不显示。
    //
    // 所以这里改成: 先备份传入的完整列表, 放行原方法正常执行(保证赋值
    // 逻辑不受影响), 执行完之后如果开关开启, 再用反射找到承载最终数据
    // 的 List 类型字段, 把它替换回备份的完整列表, 覆盖掉刚才被按长度
    // 裁剪过的结果。
    // ---------------------------------------------------------------
    private void hookHistoryLengthFilter(ClassLoader cl) throws Throwable {
        for (String[] candidate : HISTORY_FILTER_CANDIDATES) {
            final String fieldName = candidate[2];
            try {
                Class<?> cls = XposedHelpers.findClass(candidate[0], cl);
                Method m = cls.getDeclaredMethod(candidate[1], java.util.List.class);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!historyFilterBypassEnabled()) {
                            return;
                        }
                        Object arg = param.args[0];
                        if (arg instanceof java.util.List) {
                            param.setObjectExtra("originalList",
                                    new java.util.ArrayList<>((java.util.List<?>) arg));
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!historyFilterBypassEnabled()) {
                            return;
                        }
                        Object saved = param.getObjectExtra("originalList");
                        if (!(saved instanceof java.util.List)) {
                            return;
                        }
                        if (!fieldName.isEmpty()) {
                            if (!restoreListByFieldName(param.thisObject, fieldName,
                                    (java.util.List<?>) saved)) {
                                restoreFullList(param.thisObject, (java.util.List<?>) saved);
                            }
                        } else {
                            restoreFullList(param.thisObject, (java.util.List<?>) saved);
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    // 按精确字段名定位并替换, 找到就替换成功返回 true, 找不到返回 false
    private boolean restoreListByFieldName(Object target, String fieldName,
            java.util.List<?> original) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                if (!java.util.List.class.isAssignableFrom(f.getType())) {
                    return false;
                }
                f.setAccessible(true);
                Object current = f.get(target);
                if (current instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) current;
                    list.clear();
                    list.addAll((java.util.List<Object>) original);
                    return true;
                }
                return false;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    // 在目标对象里找第一个类型是 List 的实例字段, 把裁剪后的内容换回
    // 备份的完整列表。找不到就放弃(保底不影响原有显示, 只是长度限制
    // 依旧生效), 不抛异常影响其他功能。
    private void restoreFullList(Object target, java.util.List<?> original) {
        Class<?> c = target.getClass();
        while (c != null) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object current = f.get(target);
                        if (current instanceof java.util.List) {
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> list = (java.util.List<Object>) current;
                            list.clear();
                            list.addAll((java.util.List<Object>) original);
                            return;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
    }
}
