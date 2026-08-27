# 百度输入法 OPPO 定制版剪贴板解除限制 (v3, 纯反射版)

LSPosed/Xposed 模块, 目标 `com.baidu.input_oppo`。v3 放弃了 DexKit 满 App 动态扫描的思路(风险高, 曾导致输入法异常), 改成纯反射精确 hook 两个已确认的目标方法, 不依赖任何 native 库。

## 功能

1. **单条内容字符长度上限** —— hook `com.baidu.xn1#r(String)`, 长度在设置里的上限以内时原样放行, 跳过内部截断逻辑; 超过上限则让原始逻辑正常执行(保留一个兜底截断而不是完全无限制)。这个类名/方法名是从实际反编译确认过的, 不是猜的。
2. **复制去重限制解除** —— hook `com.baidu.input.ime.front.clipboard.Record#t()`(MD5 计算方法), 让重复内容也各自保留独立的历史记录。`Record` 这个类名百度自己没有混淆, 相比单字母类名更不容易随版本更新失效。

两处 hook 各自用 try/catch 单独包裹, 任意一处目标方法在某个版本上不存在, 只会跳过那一处, 不影响另一处, 不会导致输入法崩溃。

## 已知局限

- `com.baidu.xn1` 是这次这个具体版本的混淆类名, **不保证**每次百度输入法更新后类名不变(混淆映射每次编译可能不同)。如果解除失效, 需要重新反编译确认新的类名(方法名 `r` 在不同版本的观察中比较稳定)。
- 之前调研过的"剪贴板历史记录 14000 字符不显示"这个阈值(`ej2$g` 类)没有在实际设备上验证过对应关系, 这版没做, 避免引入未经确认的风险点。

## 使用方法

1. Actions 页面下载构建产物, 或手动 workflow_dispatch 触发
2. 安装, LSPosed 中激活模块, 作用域勾选 `com.baidu.input_oppo`
3. 打开 App 设置字符长度上限、去重绕过开关, 保存
4. 重启百度输入法生效

设置读取依赖 LSPosed 的 [New XSharedPreferences](https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences)(`xposedminversion=93`)。
