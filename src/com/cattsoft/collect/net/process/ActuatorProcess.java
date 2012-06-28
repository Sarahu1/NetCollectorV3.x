package com.cattsoft.collect.net.process;

import java.util.concurrent.Callable;

import com.cattsoft.collect.net.actuator.Command;

/**
 * 命令执行进程.
 * @author ChenXiaohong
 */
public class ActuatorProcess implements Callable<ProcessResult>{
	private Command command;
	private int index;
	private ProcessRunner runner;
	
	/**
	 * @param directory [命令目录]
	 * @param command  命令集
	 * @param index 当前命令在命令集({@link Command})中的下标
	 */
	public ActuatorProcess(String directory, Command command, int index) {
		this.command = command;
		this.index = index;
		//创建进程运行器
		runner = new ProcessRunner(directory, command.deal(index));
	}
	
	public ProcessResult call() throws Exception {
		String result = runner.run();
		return new ProcessResult(result, command, command.getTemplate(), command.getParams(index));
	}
}