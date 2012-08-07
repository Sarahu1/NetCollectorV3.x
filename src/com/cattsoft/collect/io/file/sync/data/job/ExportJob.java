/**
 * 
 */
package com.cattsoft.collect.io.file.sync.data.job;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.cattsoft.collect.net.reader.LineReader;

/** 数据库数据导出任务.
 * 根据指定SQL,将符合条件的数据导出到文件.
 * @author ChenXiaohong
 *
 */
public class ExportJob implements Job {
	private Logger logger = Logger.getLogger("export_job");
	/*** 数据导出路径 */
	private String pathname = null;
	/*** 数据导出SQL */
	private String sql = null;
	
	/**
	 * 默认构造
	 */
	public ExportJob() {
		// 
	}
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		JobDataMap dataMap = context.getJobDetail().getJobDataMap();
		logger = Logger.getLogger(context.getJobDetail().getName());
		// 参数设置
		this.pathname = dataMap.getString("pathname");
		this.sql = dataMap.getString("sql");
		// 读取行号位置
		long start = getBreakPoint(context.getJobDetail().getName());
		long end = Long.valueOf(dataMap.getString("records"));
		
		logger.info("获取数据库连接..");
		
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		// 当前数据ID号
		long rownum = start;
		try {
			// 获取数据连接
			try {
				conn = getConnection(dataMap.getString("driver"),
						dataMap.getString("url"), dataMap.getString("username"),
						dataMap.getString("password"));
			} catch (SQLException e) {
				logger.severe("获取数据库连接失败!请检查连接." + e.getMessage());
				// 直接抛出异常
				throw e;
			}
			// 替换起始行, 末尾行
			String sql = dataMap.getString("sql").replace("{identity}", String.valueOf(start)).replace("{records}", String.valueOf(end));
			
			logger.info("SQL:" + sql);
			
			ps = conn.prepareStatement(sql);
			
			logger.info("正在查询数据...");
			// 查询
			rs = ps.executeQuery();
			File file = new File(pathname);
			try {
				// 文件创建
				if(null != file.getParentFile())
					file.getParentFile().mkdirs();
				file.delete();
				file.createNewFile();
			} catch (Exception e) {
			}
			logger.info("查询完成, 正在导出数据...");
			// 数据列
			Set<String> columns = new LinkedHashSet<String>();
			// 注意此处从第2列开始获取, 去除了第一列的ROWNUM值
			for (int i = 2; i <= rs.getMetaData().getColumnCount(); i++)
				columns.add(rs.getMetaData().getColumnName(i));
			
			Writer writer = null;
			try {
				writer = new FileWriter(file);
				StringBuffer line = new StringBuffer();
				long count = 0;
				// 遍历所有记录
				while(rs.next()) {
					count ++;
					// 获取第一列值ID
					rownum = rs.getLong(1);
					for (String column : columns)
						line.append(rs.getString(column)).append(",");
					// 去除末尾逗号
					line.setLength(line.length() - 1);
					line.append("\n");
					// 缓存数据
					if(count % 1000 == 0) {
						writer.write(line.toString());
						writer.flush();
						line.setLength(0);
					}
				}
				// 写入数据到文件
				writer.write(line.toString());
				logger.info("共导出 " + rs.getRow() +" 条记录, 文件:" + file.getAbsolutePath());
				writer.flush();
				// 设置结果
				context.setResult(file.getAbsolutePath());
			} catch (IOException e) {
				logger.severe("数据导出失败!" + e.getMessage());
				throw new JobExecutionException("数据导出过程出现异常!", e);
			} finally {
				try {
					if(null != writer)
						writer.close();
				} catch (Exception e2) {
				}
			}
		} catch (ClassNotFoundException e) {
			throw new JobExecutionException("无法加载数据库驱动, 请检查依赖包是否存在", e);
		} catch (SQLException e) {
			throw new JobExecutionException("导出数据时出现异常!", e);
		} finally {
			close(conn, ps, rs);
		}
		try {
			saveBreakPoint(context.getJobDetail().getName(), rownum);
		} catch (IOException e) {
			logger.severe("数据导出断点位置保存失败!将影响数据导出正确性!" + e.getMessage());
		}
		logger.info("数据导出完成.");
	}
	
	/** 保存断点位置
	 * @param breakPoint
	 * @throws IOException 
	 */
	private void saveBreakPoint(String jobName, long breakPoint) throws IOException {
		File file = new File(jobName.replace(".", "_") + "_skip.conf");
		file.delete();
		file.createNewFile();
		
		Writer writer = new FileWriter(file, false);
		try {
			// 写入断点值
			writer.write(String.valueOf(breakPoint));
			writer.write("\n");
			writer.flush();
		} finally {
			writer.close();
		}
	}
	
	/** 读取上次数据导出行位置
	 * @param jobName 任务名称
	 * @return
	 */
	private long getBreakPoint(String jobName) {
		String skipPath = jobName.replace(".", "_") + "_skip.conf";
		//断点行初始设置为0
		long skip = 0;
		File file = new File(skipPath);
		if(file.exists()) {
			//读取首行数据并设置断点记录
			FileReader reader = null;
			try {
				reader = new FileReader(file);
				LineReader lineReader = new LineReader(reader);
				String line = lineReader.readLine();
				//设置值
				skip = Integer.parseInt(line);
			} catch (Exception e) {
				//断点读取记录数据读取失败
				//logger.error("Breakpoint read failed to read the recorded data!{}", e.getMessage());
			} finally {
				try {
					if(null != reader)
						reader.close();
				} catch (Exception e) {
					//
				}
			}
		} else {
			//未找到断点读取记录文件
			//logger.debug("Not found the breakpoint to read the file:{}", skipPath);
		}
		return skip;
	}
	
	/** 获取数据库连接
	 * @param driver 驱动
	 * @param url 路径
	 * @param user 用户名
	 * @param pswd 密码
	 * @return 数据连接
	 * @throws ClassNotFoundException 
	 * @throws SQLException 
	 */
	private Connection getConnection(String driver, String url, String user, String pswd) throws ClassNotFoundException, SQLException {
		// 注册
		Class.forName(driver);
		// 获取连接
		return DriverManager.getConnection(url, user, pswd);
	}
	
	/** 关闭数据连接
	 * @param conn 连接
	 * @param ps
	 * @param rs 记录集
	 */
	private void close(Connection conn, Statement ps, ResultSet rs) {
		try {
			rs.close();
		} catch (Exception e) {
		}
		try {
			ps.close();
		} catch (Exception e) {
		}
		try {
			conn.close();
		} catch (Exception e) {
		}
	}
	
	/**
	 * @return the pathname
	 */
	public String getPathname() {
		return pathname;
	}
	
	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}
}
