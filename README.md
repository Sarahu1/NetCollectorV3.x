云平台基础数据采集系统
======================

* [COPYRIGHT©2009 DATANG SOFTWARE CO.LTD](http://www.cattsoft.com/)

日志
--------------
* [3.0.1.b1] 增加数据导出发布服务,通过配置对数据库数据导出后通过FTP发布到各服务器
* [3.0.0] 发布


标记
--------------

程序需要以下运行时类库支持,放于根lib目录下

* [commons-logging-1.1.1.jar](http://commons.apache.org/logging/) -- `日志支持`
* [commons-net-3.1.jar](http://commons.apache.org/net/) -- `网络访问`
* [logback-classic-1.0.0.jar](http://logback.qos.ch/) -- `日志支持`
* [logback-core-1.0.0.jar](http://logback.qos.ch/) -- `日志支持`
* [quartz-all-1.8.6.jar](http://quartz-scheduler.org/) -- `作业调度`
* [slf4j-api-1.6.4.jar](http://www.slf4j.org/) -- `日志支持`
* [spring.jar](http://www.springsource.org/) -- `Spring支持`


支持
-------

采集程序支持多个命令的运行,以下为已配置的常用命令.
更多支持请添加配置更多的命令.


### 系统命令

1.  dig xxx
2.  dig xxx +trace
3.  dig @xxx cn ns  `二期合并`
4.  dig +norec @xxx HOSTNAME.BIND CHAOS TXT `二期合并`
5.  scamper -c "ping -c 10" -i xxx
6.  scamper -c "trace -P ICMP -M -l 5" -i xxx

### 管理

在Linux终端中使用以下命令执行根目录下的`shell.sh`脚本文件,
启动/停止/重启采集程序:

    sh shell.sh start/stop/restart [daily]

运行命令后跟`start/stop/restart`中的一个,对程序进行启动/停止/重启
管理.启动/停止命令后跟`daily`参数表示启动/停止忙时采集任务,
具体的忙时采集任务运行周期由[quartz](http://quartz-scheduler.org/)调试支持.

启动/停止监测点数据状态监控服务:

    sh shell.sh start/stop report

report代表监测点状态数据监控服务.
服务启动后可使用客户端连接该服务,
对监测点状态进行监控.
启动时需要根据各服务器的不同,修改启动脚本中的服务IP地址.


### 模块

#### 终端采集
--------------
* [collector_net_3.0.0.jar]

目录结构如下

* [conf] -- `配置目录`
   * -- collect.properties  `常规任务配置`
   * -- collect_config.xml  `Spring 配置文件`
   * -- `其它all_url.txt/all_ip.txt/daily_ip.txt等数据文件`
   * -- collect_daily.properties  `忙时任务配置`
   * -- collect_phase2.properties `二期采集配置`
* [lib] -- `支持库`
   * -- collector_net_3.0.0.jar  `采集模块`
   * -- commons-logging-1.1.1.jar
   * -- commons-net-3.1.jar
   * -- logback-classic-1.0.0.jar
   * -- logback-core-1.0.0.jar
   * -- quartz-all-1.8.6.jar
   * -- slf4j-api-1.6.4.jar
   * -- spring.jar
   * -- sunjce_provider.jar
* -- logback.xml  `日志配置`
* -- monitor  `监测点标识文件,文件内容为监测点编号`
* -- monitor_map.cf  `监测点对照`
* -- shell.sh  `管理脚本`


#### 文件同步
--------------
* [collector_sync_3.0.0.jar]

目录结构如下

* [conf] -- `配置目录`
   * -- sync.properties  `同步配置文件`
* [lib] -- `支持库`
   * -- collector_sync_3.0.0.jar  `同步模块`
   * -- commons-logging-1.1.1.jar
   * -- commons-net-3.1.jar
   * -- logback-classic-1.0.0.jar
   * -- logback-core-1.0.0.jar
   * -- slf4j-api-1.6.4.jar
* -- logback.xml  `日志配置`
* -- shell.sh  `管理脚本`


#### 文件合并
--------------
* [collector_merge_3.0.0.jar]

目录结构如下

* [conf] -- `配置目录`
   * -- merge.properties  `合并配置文件`
* [lib] -- `支持库`
   * -- collector_merge_3.0.0.jar  `数据合并模块`
   * -- commons-logging-1.1.1.jar
   * -- logback-classic-1.0.0.jar
   * -- logback-core-1.0.0.jar
   * -- slf4j-api-1.6.4.jar
* logback.xml -- `日志配置`
* shell.sh -- `管理脚本`


#### 智能告警
--------------
* [collector_alarm_3.0.0.jar]

目录结构如下


#### 数据发布
--------------
* [collector_publish_3.0.0.jar]

目录结构如下

* [conf] -- `配置目录`
   * -- export.properties  `导出配置文件`
   * -- ftp_server.list  `数据更新服务器列表`
* [lib] -- `支持库`
   * -- collector_publish_3.0.0.jar  `数据发布模块`
   * -- common-configuration-1.8.jar  `属性文件读取`
   * -- commons-lang-2.6.jar  `Apache 工具包`
   * -- commons-logging-1.1.1.jar  `日志记录`
   * -- mail.jar  `邮件通知`
   * -- ojdbc5.jar  `Oracle数据库驱动`
   * -- quartz-all-1.8.5.jar  `计划任务包`
   * -- slf4j-api-1.6.4.jar
   * -- sunjec_provider.jar
* shell.sh -- `管理脚本`


#### 终端管理
--------------
* [collector_manage_3.0.0.jar]

见使用说明


#### 配置完成后使用`shell.sh`脚本文件对程序进行启动/停止的管理

使用
--------------
终端管理程序可直接启动,为可视化窗口程序,若无法直接双击启动,可使用以下命令启动:

    javaw/java -jar collector_manage_3.0.0.jar

> javaw 启动方式无输出窗口.
java 命令启动方式会出现输出窗口,可用于查看日志


测试
--------------


贡献
--------------

1. ..


Coming Soon
--------------
2. 桌面监测工具增加监测点配置文件修改更新
3. 桌面监测工具增加监测点进程批量重启/停止功能
4. **二期采集功能合并**
5. **智能告警**

