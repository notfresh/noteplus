key0 Dxxble06x6

# 应用名称

应用名称的定义是在 strings.xml 中，然后在 AndroidManifest.xml 中通过 android:label="@string/app_name" 引用
修改后需要重新构建并安装应用才能看到变化
如果你想为不同语言设置不同的应用名称，可以创建特定语言的字符串资源文件，如 values-zh/strings.xml 用于中文
应用名称在以下位置引用：
AndroidManifest.xml 的 application 标签中
有时也会在活动的 activity 标签中，作为活动的标题
修改后重新安装应用，新名称就会显示在手机的应用抽屉和设置中。

# 增加消息提醒功能

也即是系统的下拉消息区。

