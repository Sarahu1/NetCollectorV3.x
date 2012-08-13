package com.cattsoft.collect.manage.ui;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.swing.table.DefaultTableModel;

import com.cattsoft.collect.manage.data.MonitorStatus;

/** 监测点状态信息表格模块
 * @author ChenXiaohong
 *
 */
public class MonitorModel extends DefaultTableModel {
	private static final long serialVersionUID = 1L;
	private Set<Integer> update_row = new HashSet<Integer>();
	public final static String[] columnNames = {"编号", "名称", "地址", "状态", "类型", "更新"};  
	public static Object[][] values = {};
	/*** 数据列表*/
	private List<MonitorStatus> status_list = new LinkedList<MonitorStatus>();
	
	private boolean preError = false;
	
	
	public MonitorModel() {
		super(values, columnNames);
	}
	
	public Set<Integer> getUpdateRow() {
		Set<Integer> ur = new HashSet<Integer>();
		ur.addAll(update_row);
		update_row.clear();
		return ur;
	}
	
	public boolean isPreError() {
		return preError;
	}
	
	/** 添加数据记录
	 * @param status 状态信息
	 */
	public void addRow(MonitorStatus status) {
		boolean isupdate = false;
		int monitor = 0;
		try {
			monitor = Integer.parseInt(status.getMonitor());
		} catch (Exception e) {
			preError = true;
			return;
		}
		String type = status.getType();
		type = ("daily".equalsIgnoreCase(type) ? "忙时" : ("permanent"
				.equalsIgnoreCase(type) ? "常规" : ("report"
				.equalsIgnoreCase(type) ? "统计" : ("phase2".equalsIgnoreCase(type) ? "二期" : type))));
		Object[] value = new Object[] {
				monitor,
				status.getName(),
				status.getAddress(),
				status.getStat().replace("Sl", "正常"),
				type,
				status.getUpdate().replace("s", " 秒前")
						.replace("m", " 分钟前") };
		for (int i = 0; i < status_list.size(); i++) {
			MonitorStatus ms = status_list.get(i);
			if(ms.getMonitor().trim().equalsIgnoreCase(status.getMonitor().trim())
					&& ms.getType().trim().equalsIgnoreCase(status.getType().trim())) {
				isupdate = true;
				update_row.add(i);
				// 更新表格数据
				for (int j = 0; j < getColumnCount(); j++) {
					setValueAt(value[j], i, j);
				}
			}
		}
		if(!isupdate) {
			// 添加
			addRow(value);
			status_list.add(status);
			update_row.add(status_list.size() - 1);
		}
		fireTableDataChanged();
	}
	
	public void removeRow(int row) {
		status_list.remove(row);
		super.removeRow(row);
	}
	
	public void fireTableRowsDeleted(int firstRow, int lastRow) {
		if(getRowCount() == 0)
			status_list.clear();
		super.fireTableRowsDeleted(firstRow, lastRow);
	}
	
	/**
	 * @param row 行下标
	 * @return 状态数据 {@link MonitorStatus}
	 */
	public MonitorStatus getValue(int row) {
		return status_list.get(row);
	}
	
	public void clear() {
		try {
			setRowCount(0);
			status_list.clear();
			fireTableDataChanged();
		} catch (Exception e) {
		}
	}
	
	/* 获取字段数据类型,对字段排序需要
	 * @see javax.swing.table.AbstractTableModel#getColumnClass(int)
	 */
	public Class<? extends Object> getColumnClass(int col) {
		try {
			Vector<?> v = (Vector<?>) dataVector.elementAt(0);
			return v.elementAt(col).getClass();
		} catch (Exception e) {
			preError = true;
		}
		try {
			return super.getColumnClass(col);
		} catch (Exception e) {
			preError = true;
		}
		return String.class;
	}
	
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}
}
