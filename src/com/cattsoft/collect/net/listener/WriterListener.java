/**
 * 
 */
package com.cattsoft.collect.net.listener;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.net.adapter.BaseAdaptor;
import com.cattsoft.collect.net.process.ProcessResult;

/**
 * 数据写入器监听.
 * @author ChenXiaohong
 *
 */
public abstract class WriterListener extends BaseListener {
	private Logger logger = LoggerFactory.getLogger(getClass());
	/*** 数据处理适配器*/
	protected List<BaseAdaptor> adapters = new LinkedList<BaseAdaptor>();
	/*** 数据表达式字典*/
	protected Map<String, String> dictionary = new HashMap<String, String>();
	/*** 进程退出处理进程*/
	protected Thread shutdownHook;
	/*** 缓存数据上限,超过该值时数据将写入到文件*/
	protected int limit = 10;
	/*** 缓存数据*/
	protected List<String> cache = new LinkedList<String>();
	/*** 数据文件保存路径*/
	protected String path;
	/*** Default*/
	protected final String defpath;
	/*** 是否删除记录文件*/
	protected boolean deleted = false;
	/*** 数据记录是否追加到文件*/
	protected boolean append = true;
	/*** 记录数据是否经过适配器处理,避免出现重复适配*/
	protected boolean processed;
	/*** 数据文件是否根据重定向次数自动编号*/
	protected boolean autoNumber = true;
	/*** 数据重新写入次数(数据重定向次数)*/
	protected int frequency = 0;
	/*** 最大重定向次数(控制文件重新写入次数)*/
	protected int maxFrequency = 0;
	/*** 是否删除重定向后的断点记录文件*/
	protected boolean frequencyDelete = false;
	/*** 适配表达式*/
	protected final String expression;
	/*** 适配正则*/
	protected Pattern pattern;
	/*** 适配表达式宏匹配{\w*}*/
	protected Pattern macro;
	/*** 内容适配正则*/
	protected String regex;
	/*** 是否启用写入器*/
	protected boolean enabled = true;
	
	public WriterListener(String path, String expression) {
		this.expression = expression;
		this.path = path;
		this.defpath = path;
		dictionary.put("newline", line_separator);
	}

	/** 添加适配器
	 * @param adapter 适配器
	 * @return 是否成功添加
	 */
	public boolean addAdapter(BaseAdaptor adapter) {
		return adapters.add(adapter);
	}

	/** 移除适配器
	 * @param index 下标
	 * @return 删除的适配器
	 */
	public BaseAdaptor removeAdapter(int index) {
		return adapters.remove(index);
	}

	/**
	 * 缓存数据刷新.
	 */
	public void flush() {
		try {
			if(cache.size() > 0) {
				File file = new File(path);
				if(!file.exists()) {
					file.getParentFile().mkdirs();
					file.createNewFile();
				}
			}
		} catch (Exception e) {
			//数据文件创建失败
			logger.error("Data file creation failed!{}" + e.getMessage());
		}
	}

	/**
	 * 清除适配器
	 */
	public void cleanAdapter() {
		adapters.clear();
	}

	/** 适配器处理数据
	 * @param result 数据包
	 */
	public ProcessResult process(ProcessResult result) {
		if(!processed) {
			for (BaseAdaptor adapter : adapters) {
				result = adapter.process(result);
			}
		}
		return result;
	}

	public void begin() {
		super.begin();
		if(maxFrequency > 0) {
			frequency++;
		}
		if(getFrequency() > 1 && isAutoNumber() && (getFrequency() <= getMaxFrequency())) {
			//文件编号从1开始
			int index = frequency - 1;
			//处理文件名
			//文件名中.的位置
			Pattern pattern = Pattern.compile("(.*?_)(\\d+)(\\.\\w+)$");
			Matcher matcher = null;
			int symbol = path.lastIndexOf(".");
			symbol = ((symbol < 0) ? path.length() : symbol);
			matcher = pattern.matcher(path);
			if(matcher.matches()) {
				path = matcher.group(1) + index + matcher.group(3);
			} else {
				path = path.substring(0, symbol) + "_" + index + path.substring(symbol);
			}
			setDeleted(isFrequencyDelete());
		}
		shutdownHook = new Thread(new Runnable() {
			public void run() {
				//正在关闭数据写入进程监听器
				logger.debug("Is close the data is written to process listeners..");
				if(cache.size() > 0) {
					//正在刷新缓存数据
					logger.info("Cached data is being refreshed..");
					flush();
				}
			}
		});
		//添加进程意外退出监听
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}
	
	/** 默认将判断是否需要删除数据文件
	 * @see com.cattsoft.collect.net.listener.BaseListener#finish()
	 */
	public void finish() {
		super.finish();
		//刷新缓冲区数据
		flush();
		//是否删除数据文件
		if(deleted) {
			//删除数据文件处理
			try {
				File file = new File(path);
				if(file.exists()) {
					boolean delete = file.delete();
					//删除数据文件
					logger.info("Delete the data file:{} - {}", file.getPath(), (delete ? "Successfully" : "Failure"));
				}
			} catch (Exception e) {
				logger.error("data file delete fail!path:{},error:{}", path, e.toString());
			}
		}
		//移除意外退出监听线程
		Runtime.getRuntime().removeShutdownHook(shutdownHook);
	}
	
	public void complete() {
		super.complete();
		path = defpath;
		frequency = 0;
	}

	/** 添加字典值
	 * @param key 键
	 * @param value 值
	 * @return 值
	 */
	public String setDictionary(String key, String value) {
		return dictionary.put(key, value);
	}

	/** 默认当缓冲区达到上限值时将刷新缓冲区数据
	 * @see com.cattsoft.collect.net.listener.BaseListener#afterExec(com.cattsoft.collect.net.process.ProcessResult, int)
	 */
	public void afterExec(ProcessResult result, int index) {
		// 写入结果添加命令行内容支持
		try {
			dictionary.put("command", Arrays.toString(result.getCommand().deal(result.getParams())));
		} catch(Exception e) {
			logger.error("add deal command to dictionary failed!{}", e.getMessage());
		}
		//适配处理
		process(result);
		super.afterExec(result, index);
		//判断缓存区大小
		if(cache.size() >= limit) {
			//刷新数据缓存区
			flush();
		}
	}

	/**
	 * 根据数据内容表达式处理数据
	 * @param result 数据内容
	 * @return 已处理的数据
	 */
	protected String expressionProcess(String result, String...values) {
		String content = result;
		dictionary.put("date", DATE_SDF.format(System.currentTimeMillis()));
		dictionary.put("time", TIME_SDF.format(System.currentTimeMillis()));
		dictionary.put("result", content);
		if(this.expression != null && !"".equals(expression)) {
			//根据表达式处理数据
			Matcher matcher = Pattern.compile("\\{(\\w*)\\}").matcher(expression);
			int curr_match = 0;
			String message = expression;
			try {
				while(matcher.find()) {
					String key = matcher.group(1);
					String macro = matcher.group(0);
					if("".equals(key)) {
						message = message.replaceFirst(Pattern.quote(macro), values[curr_match]);
						curr_match++;
					} else {
						if(key.matches("\\d+")) {
							// 参数下标可能出现长度不一的情况,忽略
							int index = Integer.parseInt(key);
							String value = index < values.length ? values[index] : "";
							message = message.replace(macro, value);
						} else
							message = message.replace(macro, dictionary.get(key));
					}
					content = message;
				}
			} catch (Exception e) {
				//处理内容表达式时出现错误
				logger.error("An error occurred while processing the content expression:{}.{}", expression, e.getMessage());
			}
		}
		return content;
	}

	/**
	 * @param deleted 是否完成时删除记录文件
	 */
	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	/**
	 * 设置数据是否为追加到文件
	 * @param append 是否追加
	 */
	public void setAppend(boolean append) {
		this.append = append;
	}

	/**
	 * @return 是否已经过适配处理
	 */
	public boolean isProcessed() {
		return processed;
	}

	/**
	 * 设置缓冲区大小
	 * @param limit 数据缓存区大小
	 */
	public void setLimit(int limit) {
		this.limit = limit;
	}

	/**
	 * @return 数据缓存区大小
	 */
	public int getLimit() {
		return limit;
	}
     /**
     ** @param maxFrequency 文件最大重定向次数
	 */
	public void setMaxFrequency(int maxFrequency) {
		this.maxFrequency = maxFrequency;
	}

	/**
	 * @return 文件最大重定向次数
	 */
	public int getMaxFrequency() {
		return maxFrequency;
	}

	/**
	 * @return 当前重定向次数
	 */
	public int getFrequency() {
		return frequency;
	}

	/**
	 * @param autoNumber 是否自动编号
	 */
	public void setAutoNumber(boolean autoNumber) {
		this.autoNumber = autoNumber;
	}

	/**
	 * @return 当前数据文件是否自动编号
	 */
	public boolean isAutoNumber() {
		return autoNumber;
	}

	/**
	 * @param frequencyDelete  是否自动删除重定向的记录数据文件
	 */
	public void setFrequencyDelete(boolean frequencyDelete) {
		this.frequencyDelete = frequencyDelete;
	}

	/**
	 * @return 是否删除重定向的记录数据文件
	 */
	public boolean isFrequencyDelete() {
		return frequencyDelete;
	}

	/**
	 * @param adapters 数据适配器列表
	 */
	public void setAdapters(List<BaseAdaptor> adapters) {
		this.adapters = adapters;
	}

	/**
	 * @return 数据适配器列表
	 */
	public List<BaseAdaptor> getAdapters() {
		return adapters;
	}

	/**
	 * @return 是否删除记录文件
	 */
	public boolean isDeleted() {
		return deleted;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	/***
	 * @param enabled 是否启用
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}