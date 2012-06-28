/**
 * 
 */
package com.cattsoft.collect.manage.ui;

import java.awt.Color;
import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.cattsoft.collect.manage.data.MonitorStatus;
import com.cattsoft.collect.manage.logging.LogManager;

/** 采集点状态监控表格.
 * @author ChenXiaohong
 *
 */
public class MonitorTable extends JTable {
	private Logger logger = LogManager.getLogger(getClass().getSimpleName());
	private static final long serialVersionUID = 1L;
	private MonitorModel model;
	/*** 状态栏,用于显示数据*/
	private JToolBar toolBar;
	private SimpleDateFormat time_sdf = new SimpleDateFormat("HH:mm:ss");
	/*** 命令状态更新超时(分) */
	private int timeout = 3;
	/*** 缓存数据*/
	//	private Map<String, Map<String, Object[]>> cache = Collections
	//			.synchronizedMap(new LinkedHashMap<String, Map<String, Object[]>>());

	public MonitorTable() {
		model = new MonitorModel();
		// 排序
		setModel(model);
		setAutoCreateRowSorter(true);
		setRowSorter(new TableRowSorter<MonitorModel>(model));
		setRowHeight(22);
		//使表格表头的字体居中，若想居左居右，只要改变其属性
		((DefaultTableCellRenderer) tableHeader.getDefaultRenderer())
		.setHorizontalAlignment(SwingConstants.CENTER);
		// 第一列宽度
		tableHeader.getColumnModel().getColumn(0).setPreferredWidth(20);
		model.addTableModelListener(new TableModelListener() {
			public void tableChanged(TableModelEvent e) {
				if(e.getType() ==  TableModelEvent.INSERT) {
					// 添加
				}
				if(e.getType() == TableModelEvent.UPDATE) {
					// 更新
				}
				// 状态栏更新
				if(null != toolBar) {
					((JLabel)toolBar.getComponentAtIndex(0)).setText("数据已更新");
					((JLabel)toolBar.getComponentAtIndex(2)).setText("共" + model.getRowCount() + "记录");
					((JLabel)toolBar.getComponentAtIndex(4)).setText(time_sdf.format(System.currentTimeMillis()));
				}
			}
		});
		// 记录选择事件
		getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if(getSelectedRowCount() > 1) {
					((JLabel)toolBar.getComponentAtIndex(0)).setText(getSelectedRowCount()+" 记录已选择");
				} else {
					((JLabel)toolBar.getComponentAtIndex(0)).setText("数据已更新");
				}
			}
		});
	}
	
	public void setToolBar(JToolBar toolBar) {
		this.toolBar = toolBar;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/* 数据列表格渲染
	 * @see javax.swing.JTable#getCellRenderer(int, int)
	 */
	public TableCellRenderer getCellRenderer(int row, int column) {
		return new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getTableCellRendererComponent(JTable table, Object value,
					boolean isSelected, boolean hasFocus, int row, int column) {
				// 列数据水平居中
				setHorizontalAlignment(SwingConstants.CENTER);
				setToolTipText(null);
				setBackground(null);
				try {
					// 通过行排序类获取真实行数据下标,避免重新排序后行颜色不变的情况
					int modelRow = getRowSorter().convertRowIndexToModel(row);
					// 根据状态信息命令更新情况,设置数据行背景颜色
					MonitorStatus status = ((MonitorModel)table.getModel()).getValue(modelRow);
					Map<String, Map<String, String>> cmd_status = status.getCommandMap();
					// 已超时的命令名称
					StringBuilder cmd_name = new StringBuilder();
					// 当前正在执行的命令
					StringBuilder cmd_curr = new StringBuilder("<html>");
					// 标记命令是否出现超时
					boolean foundTimeout = false;
					// 命令是否正在执行
					boolean isrunning = false;
					// 循环检查命令状态
					for (Map.Entry<String, Map<String, String>> entry_map : cmd_status.entrySet()) {
						Map<String, String> entry = entry_map.getValue();
						// 对非DONE状态命令进行检查
						if(!"DONE".equalsIgnoreCase(entry.get("progress"))) {
							long update = 0l;
							try {
								update = Long.valueOf(entry.get("update"));
							} catch (Exception e) {
								e.printStackTrace();
								// 转换过程出现异常
							}
							// 转换为分钟后,判断是否超时
							if((update / 60l) > timeout) {
								foundTimeout = true;
								// 设置状态列值为"异常"
								setValueAt("异常", modelRow, 3);
								cmd_name.append(entry_map.getKey()).append("/");
							}
							if(!foundTimeout) {
								try {
									long size = Long.valueOf(entry.get("size"));
									long progress = Long.valueOf(entry.get("progress"));
									// 进度是否小于Size
									if(progress < size) {
										isrunning = true;
										cmd_curr.append(entry_map.getKey())
										.append("[").append(size)
										.append("/").append(progress)
										.append("]").append("<br>");
									}
								} catch (Exception e) {
									// 转换出现异常
								}
							}
						}
					}
					if(foundTimeout) {
						// 去除命令之间的分隔符
						cmd_name.setLength(cmd_name.length() > 0 ? cmd_name
								.length() - 1 : cmd_name.length());
						// 设置背景色为红色
						setBackground(Color.RED);
						setToolTipText(cmd_name.toString() + "命令执行状态出现超时");
					} else if (isrunning) {
						cmd_curr.append("</html>");
						// 标记当前命令正在运行
						setBackground(Color.GREEN);
						setToolTipText(cmd_curr.toString());
					} else if (status.getStat().endsWith("+")) {
						// 新添加记录
						setBackground(new Color(225, 255, 255));
					}
				} catch (Exception e) {
					logger.severe("设置表格单元格格式时出现异常!" + e.toString());
				}
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
						row, column);
				return this;
			}
		};
	}
	
	/**
	 * @param status
	 */
	public void operate(final MonitorStatus status) {
		// 开辟线程进行处理
		new Thread(new Runnable() {
			public void run() {
				new MonitorStatusOperate(null, status);
			}
		}, "status_operate").start();
	}
}
