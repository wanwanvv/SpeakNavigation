# SpeakNavigation
基于语音交互的智能导航系统

## Codes
整体分为两部分，第一部分：路径描述语言理解，第二部分：模板驱动路径搜索

第一部分服务器端为Server文件夹，使用Pycharm开发，其中CompressJointBert为已训练好的模型，Server为服务器端代码文件，可使用ipconfig查看本地ip替换Server.py文件中的242行。

第二部分服务器端为ServletDemo文件夹，使用IDEA开发。首先配置Java环境，然后搭建Tomcat，分别使用jdk1.8.0
和tomcat 8.5.81，在运行调试配置里面更换URL为本地ip。

android端的代码为navigation文件夹，使用android studio。在'navigation\app\src\main\java\com\graphhopper\navigation\example'路径下的DefaultConfig文件中更换String IP。
