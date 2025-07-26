## 项目结构

* `scanner`：该模块通过使用Soot框架提供的API来扩展FlowDroid功能，对打印日志语句中的变量进行数据流分析，追踪每个变量所经过的函数并记录。最终输出一个json文件，其中包含所提取出每条日志的类名`log_classname`、字符串`log_string`、所在类名`classname`、所在函数名`function_name`、日志内参数流经的函数数组`from`。

* `checker`：该模块读取大模型分析提取敏感信息的结果，通过静态分析获取数据流经的函数，按照函数方法签名获取FlowDroid框架所解析出的函数方法体（是一种字节码指令格式），然后将该方法体输入给大模型来判断是否进行了脱敏操作。最终输出一个json文件，其中包含所提取出每条日志的类名`log_classname`、字符串`log_string`、所在类名`classname`、所在函数名`function_name`、日志内参数流经的函数数组`from`，是否为敏感信息`is_sensitive`，敏感信息key`sensitive_key`、是否经过加密或脱敏`isEncrypted`。
* `soot-infoflow`：这是 FlowDroid 的核心数据流分析引擎，独立于 Android。 它实现了 IFDS/IDE 数据流分析框架（使用 Heros），并定义了源（Sources）、汇（Sinks）、污点传播规则等核心概念。
* `soot-infoflow-android`：这个模块构建在`soot-infoflow`之上，专门处理Android应用的复杂特性。它包含Android生命周期的建模、四大组件的处理、回调机制、资源访问、权限检查等。
* `soot-infoflow-cmd`：提供命令行接口(CLI)来运行FlowDroid。
* `soot-infoflow-summaries`：包含用于常见库（特别是 Android SDK）的预计算或配置的数据流摘要（Summaries）。

## 运行环境

* Java 18+
* Maven

## 启动方式

### 编译

```
mvn -DskipTests=true package
```

### 运行

#### 运行scanner模块

```
java -jar target/check_behaviour-jar-with-dependencies.jar \
     <Android JAR folder> \
     <APK File> \
     <output file>
```

* <Android JAR folder>：Android SDK 安装文件夹内的 “platforms” 目录。
* <APK File>：APK文件的位置
* <Output File>：输出文件位置，文件格式为`json`文件

例如

```
java -jar target/check_behaviour-jar-with-dependencies.jar ./platforms BydDiLinkAccount.apk BydDiLinkAccount.json
```

#### 运行checker模块

```
java -jar target/check_behaviour-jar-with-dependencies.jar \
     <Android JAR folder> \
     <APK File> \
     <LLM Result File> \
     <Output File>
```

* <Android JAR folder>：Android SDK 安装文件夹内的 “platforms” 目录。
* <APK File>：APK文件的位置
* <JSON File>：大模型分析结果，文件格式为`json`文件
* <Output File>：输出文件位置，文件格式为`json`文件

例如

```
java -jar target/check_behaviour-jar-with-dependencies.jar ./platforms BydDiLinkAccount.apk BydDiLinkAccount_result.json BydDiLinkAccount_encrypt_result.json
```

