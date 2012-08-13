#! /bin/base
## 执行脚本启动/停止/重启应用程序.
## 不支持多进程管理,运行前请检查进程状态
## 为避免出现多个进程,建议使用重启(restart)命令
## 停止进程时将停止相应类型的所有进程
## chenxiaohong@mail.com
## 2012/08/10

#
# Usage:
# 常规采集启动/停止:
# sh shell.sh [start/stop/kill] 分别为 启动/停止 程序命令
# 忙时采集启动/停止:
# sh shell.sh [start/stop/kill] [daily/busy] 启动/停止忙时采集
# 二期采集启动/停止:
# sh shell.sh [start/stop/kill] [phase2] 启动/停止二期采集
# 监测服务启动/停止:
# sh shell.sh [start/stop] [report/gather] 启动/停止监测服务
# 同步服务启动/停止:
# sh shell.sh [start/stop] [sync/synchronize] 启动/停止同步服务
# 合并服务启动/停止:
# sh shell.sh [start/stop] [merge/compose] 启动/停止合并服务
# 数据发布启动/停止:
# sh shell.sh [start/stop] [merge/compose] 启动/停止数据发布服务
# 停止程序 stop/kill:
# sh shell.sh kill 与 sh shell stop 同为停止程序命令
# 重启程序 restart:
# sh shell.sh restart [basic/daily/report/sync/merge] 命令对进程进行重启
#

# 基础采集应用程序启动入口,各功能入口可能不一样.有变动请在其启动方法中修改
main="com.cattsoft.collect.net.main.CollectionEngine"
# 应用程序配置文件路径
config="conf/collect_config.xml"
# 应用程序属性配置文件路径
config_property="conf/collect.properties"
# 忙时采集程序属性配置文件路径
# 启动类型为忙时采集时该值将替换config_property的值,并将替换config配置文件中的值
config_daily_property="conf/collect_daily.properties"
# 二期采集程序属性配置文件路径
config_phase2_property="conf/collect_phase2.properties"
# 应用程序标志
app_label="netCollector"
# 进程类型标识:常规(permanent)/忙时(daily)/二期(phase2)/报表(report)/同步(sync)/合并(merge)
app_type="permanent"

# 启动方法入口
start() {
    ## 用户权限检查
    permissionChecks;
    ## 内部判断启动进程类型
    case $app_type in
        "permanent")
            printf "Starting the basis of acquisition process..\n"
            start_basic;;
        "daily")
            start_busy;;
        "phase2")
            start_phase2;;
        "publish")
            start_publish;;
        "report")
            start_report;;
        "sync")
            start_sync;;
        "merge")
            start_merge;;
        "all")
            ##  启动命令不支持启动所有
            printf "The startup command is not supported\n"
            exit 1;;
        *)
            printf "Startup type is not specified\n"
            exit 1;;
    esac
    status=$?;
    ## 打印进程状态信息
    printOverview $!;
    
    ## 判断退出状态值是否为 0,非0表示异常
    if [ $status -eq 0 ]; then
    	## 进程启动成功
        printf "Process is started successfully\n";
    elif [ $status -eq -1 ]; then
    	## 进程启动异常
    	printf "Abnormal to start the process!\n";
    else
    	## 进程启动失败
    	printf "Process failed to start!\n";
    fi
    ## 返回状态值
    return $status;
}

# 停止方法入口
stop() {
    permissionChecks;
    ## 内部判断停止进程类型
    case $app_type in
        "permanent" | "daily" | "phase2" | "publish" | "report" | "sync" | "merge")
            ## 停止进程,将停止所有该类型的进程
            killpid $app_type;;
        "all")
            stop_all;;
        *)
            printf "Do not specify a stop type\n"
            exit 1;;
    esac
    status=$?;
    
    ## 打印信息
    appOver="Dtype=$app_type";
    if [ $app_type == "all" ] ; then
        appOver=$app_label;
    fi
    sleep 0.5;
    printOverview $appOver;
    
    ## 判断退出状态值是否为 0,非0表示异常
    if [ $status == 0 ] ; then
    	## 进程停止成功
        printf "Process stops successfully\n";
    elif [ $status == 1 ] ; then
    	## 进程已停止或未启动
    	printf "The process has to stop or start!\n";
    else
    	## 进程停止失败
    	printf "The process stops failed!\n";
    fi
    ## 返回状态值
    return $status;
}

# 重启方法入口
restart() {
    ## 停止
    stop;
    ## 启动
    start;
    if [ $? == 0 ] ; then
    	## 进程重启成功
        printf "Process re-start to success\n";
    else
        printf "Process to restart fail!\n";
    fi
}


# 启动基础采集
start_basic() {
    # 从monitor文件中读取监测点编号
    monitor=`sed -n 1p monitor`
    # 替换配置文件监测点编号
    if [ ! -z ${monitor} ]; then
        sed -i "s/^monitor\s*=\s\w*/monitor = ${monitor}/g" ${config_property}
        printf "Monitor Number:${monitor}\n";
    fi
    ## 打印配置文件选择
    printf "Configuration file:${config}, Properties file:${config_property}\n"
    # 替换应用程序属性配置文件路径至xml配置文件
    sed -i "s#\(<property name=\"location\" value=\"\).*\(\"/>\)#\1${config_property}\2#g" ${config}
    
    # 启动应用程序
    # Logback日志配置文件出现找不到的情况,使用logback.configurationFile属性指定
    # -Dconfig 指定配置文件(默认为:conf/collect_config.xml)
    # -Dmonitor指定监测点编号
    java -Xms256m -Xmx768m -Dmonitor=${monitor} -DLabel=${app_label} -Dtype=${app_type} -Dversion=3.0 \
         -Dconfig=${config} -Dfile.encoding=UTF-8 -Dservice.file=${service_file} \
         -Dlogback.configurationFile=./logback.xml -Djava.ext.dirs=lib -classpath .:. ${main} &>/dev/null &
    processChecks $!;
    return $?;
}

# 启动忙时采集
start_busy() {
    printf "Starting the acquisition process when busy..\n";
    
    # 替换属性文件路径为忙时采集配置文件路径
    config_property=$config_daily_property
    
    ## 常规采集与忙时采集只是配置文件不同
    ## 所以可以直接替换配置文件即可
    start_basic;
    return $?;
}

# 启动二期采集
start_phase2() {
    printf "Starting the Phase II acquisition process..\n";
    
    # 替换属性文件路径为二期采集配置文件路径
    config_property=$config_phase2_property
    
    ## 二期采集与常规采集只是配置文件不同
    ## 也可以直接替换配置文件即可
    start_basic;
    return $?;
}

# 启动数据发布服务
start_publish() {
    printf "Starting to export and data publishing process..\n";
    java -Xms256m -Xmx768m -Dtype=${app_type} -DLabel=${app_label} -Dfile.encoding=UTF-8 -Dlogback.configurationFile=./logback.xml \
          -Dsync.config=conf/export.properties -Djava.ext.dirs=lib -classpath .:. com.cattsoft.collect.io.file.sync.publish.Sync &>/dev/null &
    processChecks $!;
    return $?;
}

# 启动监测报告服务
start_report() {
    printf "Starting the monitoring report service..\n";
    ## 启动监测服务,需要修改 server.host 地址
    java -Xms256m -Xmx768m -Dserver.host=202.108.49.59 -Dserver.port=8089 -Dtype=${app_type} -DLabel=${app_label} \
         -Dfile.encoding=UTF-8 -Dlogback.configurationFile=./logback.xml -Djava.ext.dirs=lib -classpath .:. \
         com.cattsoft.collect.net.report.CollectorReportStatistics &>/dev/null &
    processChecks $!;
    return $?;
}

# 启动数据同步服务
start_sync() {
    printf "Starting data synchronization service..\n";
    java -Xms256m -Xmx768m -Dtype=${app_type} -DLabel=${app_label} -Dfile.encoding=UTF-8 -Dlogback.configurationFile=./logback.xml \
          -Dsync.config=conf/sync.properties -Djava.ext.dirs=lib -classpath .:. com.cattsoft.collect.io.file.sync.ftp.Sync &>/dev/null &
    processChecks $!;
    return $?;
}

# 启动数据合并服务
start_merge() {
    printf "Starting to merge data services..\n";
    java -Xms256m -Xmx768m -Dtype=${app_type} -DLabel=${app_label} -Dfile.encoding=UTF-8 -Dlogback.configurationFile=./logback.xml \
          -Dmerge.config=conf/merge.properties -Djava.ext.dirs=lib -classpath .:. com.cattsoft.collect.io.file.merge.FileMergeTask &>/dev/null &
    processChecks $!;
    return $?;
}

# 停止所有采集
stop_all() {
    ## 依次停止所有进程,无先后顺序
    ## 停止基础采集
    killpid 'permanent';
    ## 停止忙时采集
    killpid 'daily';
    ## 停止数据同步服务
    killpid 'sync';
    ## 停止数据合并服务
    killpid 'merge';
    ## 停止监测报告服务
    killpid 'report';
    printf "All processes have been stopped\n";
    printf "\n";
    
    ## 返回状态值
    return 0;
}

# 结束进程
killpid() {
    ## 通过ps命令获取参数类型进程PID
    pids=`ps aux | grep DLabel=$app_label | grep Dtype=$1 | grep -v "grep" | awk '{if($2 -ge 1)print $2}'`;
    ## 打印提示
    case $1 in
        "permanent")
            printf "Stopping the basis of acquisition process..\n";;
        "daily")
            printf "Stopping the acquisition process when busy..\n";;
        "phase2")
            printf "Stopping the Phase II acquisition process..\n";;
        "publish")
            printf "Stopping to export and data publishing process..\n";;
        "sync")
            printf "Stopping data synchronization services..\n";;
        "merge")
            printf "Stopping data consolidation services..\n";;
        "report")
            printf "Stopping the monitoring report service..\n";;
        "*")
            printf "\n";;
    esac
    ## 使用状态值进行标识,pids最小长度总为1
    status=1;
    ## 遍历进程编号
    for pid in ${pids[*]}
    do
    	printf "Stopping the process $pid ..\n";
    	## 结束进程
    	kill $pid >/dev/null 2>&1
    	status=0;
    	## 判断进程是否成功结束
    	if [ $? -eq 0 ]; then
    	    printf "The process $pid has been stopped successfully\n";
    	else
    	    printf "The process $pid stops fails, will be forced to stop!\n";
    	    ## 强制停止进程
    	    kill -15 $pid >/dev/null 2>&1
    	    if [ $? -eq 0 ]; then
    	        printf "The process has been forced to stop\n";
    	    else
    	        ## 革命必须彻底,必须弄死
    	        kill -9 $pid >/dev/null 2>&1
            fi
        fi
    done
    ## 返回状态值
    if [ ${#pids[*]} -gt 0 -a $status -eq 0 ]; then
        return 0;
    else
        ## 未找到相关进程
        return 1;
    fi
}

# 打印进程信息
printOverview() {
    grep=`ps aux|grep $1`
    printf "${grep}\n";
}

# 进程检查,传入参数为进程PID
# 检查进程是否存在,正常返回0,不存在时返回非0
processChecks() {
    sleep 0.5;
    # 向进程发送无效的kill命令,确认进程是否存在
    kill -0 $1;
    return $?;
}

# 脚本权限检查,检查当前登录用户是否具有root权限
permissionChecks() {
    # 检查脚本运行权限
    # Make sure only root can run our script
    if [ "$(id -u)" != "0" ]; then
        printf "This script must be run as root\n"
        # printf "Please enter root's password when prompted.\n"
        # Shoot off the su command to gain root privileges
        # su -
        # Quit the script if the user failed to provide the right password
        #if [ $? -ne 0 ] ; then
        # printf "Script is exiting because you failed to give root's password\n"
        # Exit with failure status
        exit 1
        #fi
    fi
    #
}

## 由参数指定管理程序进程类型
case "$2" in
    ## 基础采集
    "basic" | "permanent")
        app_type="permanent";;
    ## 二期采集
    "phase2")
        app_type="phase2";;
    ## 忙时采集
    "busy" | "daily")
        app_type="daily";;
    ## 数据发布
    "publish" | "export")
        app_type="publish";;
    ## 报表服务
    "report" | "gather")
        app_type="report";;
    ## 数据同步
    "sync" | "synchronize")
        app_type="sync";;
    ## 数据合并
    "merge" | "compose")
        app_type="merge";;
    ## 操作针对所有进程
    ## 只能进行停止操作
    "all" | "whole")
        app_type="all";;
    ## 打印帮助
    "help" | "-h" | "-help")
        printf "Usage:$0 $1 [permanent/daily/phase2/publish/report/sync/merge]\n"
        printf "Legend:\n"
        printf "  permanent: 常规采集\n"
        printf "  daily: 忙时采集\n"
        printf "  phase2: 二期采集\n"
        printf "  publish: 数据发布服务\n"
        printf "  report: 监测报表服务\n"
        printf "  sync: 数据同步服务\n"
        printf "  merge: 数据合并服务\n"
        # 退出
        exit 1;;
    *)
        if [[ -n $2 ]]; then
            printf "The wrong type of process\n";
            printf "Usage:$0 $1 [permanent/daily/phase2/publish/report/sync/merge]\n";
            exit 1;
        else
            ## 默认为基础采集
            app_type="permanent";
        fi;;
esac


## 由输入的参数选择运行类型
case "$1" in
    ## 运行
    "start" | "begin")
        start;;
    ## 停止
    "stop" | "kill")
        stop;;
    ## 重启
    "restart")
        restart;;
    ## 查看状态
    "status")
        grep=`ps aux|grep $app_label`
        printf "all procedures:\n"
        printf "${grep}\n";;
    *)
        printf "Usage:$0 [start|stop|restart]\n";;
esac
# Give up root access immediately after you're done performing the
# copies. This should return the process to a regular user
# exit
exit 0
