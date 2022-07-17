# SpeakNavigation
基于语音交互的智能导航系统，用户给出任意句式的希望途径的某些固定距离的POI再抵达最终目的地的描述，基于这些线索(途径的POI和对应的距离)，为用户提供更加合理的出行路线，从而提高用户的出行体验。

# Codes
整体分为两部分，第一部分：路径描述语言理解，第二部分：模板驱动路径搜索

第一部分服务器端为Server文件夹，使用Pycharm开发，其中CompressJointBert为已训练好的模型，Server为服务器端代码文件，可使用ipconfig查看本地ip替换Server.py文件中的242行。

第二部分服务器端为ServletDemo文件夹，使用IDEA开发。首先配置Java环境，然后搭建Tomcat，分别使用jdk1.8.0
和tomcat 8.5.81，在运行调试配置里面更换URL为本地ip。

android端的代码为navigation文件夹，使用android studio。在'navigation\app\src\main\java\com\graphhopper\navigation\example'路径下的DefaultConfig文件中更换String IP。

# Framework
<p align="center">
<img src=".\img\framework.png" height = "500" alt="" align=center />
<br><br>
<b>Figure 1.</b> Framework.
</p>

## User Interface Layser用户接口层
### Android客户端
+ Mapbox：
+ iFLYTEK(科大讯飞)：实现语音识别，将语音转为文本
+ graphhopper：


## Query Processing Layer查询处理层
### Flask
+ 路径描述自然语言理解：由于用户有各自偏好的自然语言表达方式，对于以任意句式输入的路径描述，我们需要准确识别出用户的意图并获取跟导航相关的重要线索，即途径的POI和对应的距离
+ 关键词解析模型：使用意图识别和槽填充联合模型JointBERT提取路径描述中关于途径地的文本信息和空间信息，并根据语义区分每个地点的途径顺序，得到途径地线索

### Tomcat/Servlet
+ 基于线索的出行路径规划：实现搜索算法以支持用户进行地点搜索操作和路径导航操作
+ 根据途径地线索，实现高效的Greedy Clue Search (GCS) 算法，为用户返回与查询相匹配的可行路径

## Storage and Index Layer存储和索引层
+ Android-MapDB
+ SFC-QuadTree：将空间填充曲线和倒排文件结合的一种混合索引

# 运行效果
## 基于线索的路径导航
<p align="center">
<img src=".\img\android1.png" height = "500" alt="" align=center />
<br><br>
<b>Figure 3.</b> 基于线索的路径导航界面.
</p>

+ 用户通过点击语音按钮向系统语音输入路径查询“I want to find a path…”
+ 系统首先将语音转化为文本（图2-a）
+ 然后提取出文本中的关键词（POIs和距离）显示在底部的信息栏（图2-b）
+ 当用户确认关键词信息无误并点击”comfirm“按钮后，地图上会显示从当前位置出发并且途径这些关键词的路线，路线上的蓝色标记是根据关键词查找到的地点，标记上的数字表明了途径地的顺序
+ 同时系统还会将路径的时间、距离等信息显示在地图下方的信息栏中（图2-c）
+ 当用户点击信息栏中的导航按钮时，系统将进行导航指令播报
+ 当地图上显示了路径后，用户可以在任何时候点击上方的POI按钮来查看途径地的具体信息（图2-d）

## POI搜索
<p align="center">
<img src=".\img\android2.png" height = "500" alt="" align=center />
<br><br>
<b>Figure 3.</b> POIs搜索界面.
</p>

+ 在上方的搜索框内输入要搜索的POI类型，SpeakNav首先会判断出这是一个POI搜索请求，然后将搜索结果标记在地图上（图3-a）
+ 同时会弹出列表显示搜索结果的名称、街道等信息（图3-b）
+ 地图标记上的数字与列表中的结果顺序相对应
+ 用户可以通过点击屏幕的其他位置关闭列表或者点击地图下方的图3-c打开列表
+ 当用户点击列表内的某个地点或者点击地图上的标记时，地图上会显示一条从当前位置到所选地点的最短路径

