/**
 * 
 */
package com.cattsoft.collect.manage.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

/** 终端对照表.
 * @author ChenXiaohong
 *
 */
public class MonitorTerminalTable extends JFrame {
	private static final long serialVersionUID = 1L;
	private JTable table;
	private JPopupMenu popupMenu;
	
	// 菜单项
	private JMenuItem menu_item_mgr;
	private JMenuItem menu_item_upload;
	private JMenuItem menu_item_choose;
	
	public MonitorTerminalTable() {
		super("终端对照表");
		popupMenu = new JPopupMenu("终端操作");
		menu_item_mgr = new JMenuItem("终端管理");
		menu_item_mgr.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new ShellInfoDialog(null, table.getValueAt(table.getSelectedRow(), 2)
						.toString(), table
						.getValueAt(table.getSelectedRow(), 1).toString(),
						null, "", null);
			}
		});
		menu_item_upload = new JMenuItem("文件上传");
		menu_item_upload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int[] rows = table.getSelectedRows();
				for (int row : rows) {
					try {
						new MonitorFtpConnect(null, table.getValueAt(row, 2)
								.toString(), table
								.getValueAt(row, 1).toString(), null, (rows.length > 1), (rows.length > 1));
					} catch (Exception e2) {
						JOptionPane.showMessageDialog(null,
								"主机(" + table.getValueAt(row, 2)
										+ ")上传文件失败!请手动上传.", "上传失败",
								JOptionPane.WARNING_MESSAGE);
					}
				}
			}
		});
		menu_item_choose = new JMenuItem("加载数据");
		menu_item_choose.setToolTipText("加载每行格式为\"编号\", \"名称\", \"地址\"的终端数据文件");
		menu_item_choose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser jfc = new JFileChooser();
				int flag = jfc.showOpenDialog(null);
				if (flag == JFileChooser.APPROVE_OPTION) {
					((TerminalTableModel) table.getModel()).setValues(UIUtils
							.getTerminalTable(jfc.getSelectedFile()
									.getAbsolutePath()));
				}
			}
		});
		popupMenu.add(menu_item_mgr);
		popupMenu.addSeparator();
		popupMenu.add(menu_item_upload);
		popupMenu.addSeparator();
		popupMenu.add(menu_item_choose);
		init();
	}
	
	private void init() {
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setSize(480, 584);
		UIUtils.addEscapeListener(this);
		
		getContentPane().setLayout(new BorderLayout());
		
		TerminalTableModel ttmodel = new TerminalTableModel();
		table = new JTable(ttmodel) {
			private static final long serialVersionUID = 1L;
			// 列渲染
			public TableCellRenderer getCellRenderer(int row, int column) {
				return new DefaultTableCellRenderer() {
					private static final long serialVersionUID = 1L;
					
					public Component getTableCellRendererComponent(JTable table, Object value,
							boolean isSelected, boolean hasFocus, int row, int column) {
						setHorizontalAlignment(SwingConstants.CENTER);
						// 颜色
						if (row % 2 == 0)
							setBackground(new Color(222, 237, 206));
						else
							setBackground(new Color(244, 239, 249));
						super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
								row, column);
						return this;
					}
				};
			}
		};
		table.setRowHeight(24);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		table.getTableHeader().setPreferredSize(new Dimension(30, 22));
		table.getTableHeader().getColumnModel().getColumn(0).setPreferredWidth(10);
		
		table.getTableHeader().setReorderingAllowed(false);
		table.setAutoCreateRowSorter(true);
		table.setRowSorter(new TableRowSorter<TerminalTableModel>(ttmodel));
		
		// 右键菜单
		table.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
//				int row = table.rowAtPoint(e.getPoint());
//				table.getSelectionModel().setSelectionInterval(row, row);
				if(e.getButton() == MouseEvent.BUTTON3) {
					if(null != popupMenu) {
						// 选择项多行行时管理项不可用
						menu_item_mgr.setEnabled(table.getSelectedRowCount() == 1);
						menu_item_upload.setEnabled(table.getSelectedRowCount() > 0);
						popupMenu.show(e.getComponent(), e.getX(), e.getY());
					}
				}
				super.mousePressed(e);
			}
		});
		
		JScrollPane scrollPane = new JScrollPane(table);
		getContentPane().add(scrollPane);
		
		scrollPane.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if(e.getButton() == MouseEvent.BUTTON3) {
					if(null != popupMenu) {
						menu_item_mgr.setEnabled(false);
						menu_item_upload.setEnabled(false);
						popupMenu.show(e.getComponent(), e.getX(), e.getY());
					}
				}
				super.mousePressed(e);
			}
		});
		UIUtils.setCenter(this);
		setVisible(true);
	}
}

class TerminalTableModel extends DefaultTableModel {
	private static final long serialVersionUID = 1L;
	private final static String[] columnNames = {"编号", "名称", "地址"};  
	
	public void setValues(Object[][] values) {
		try {
			setRowCount(0);
			fireTableDataChanged();
			for (Object[] objects : values) {
				addRow(objects);
			}
		} catch (Exception e) {
		}
		fireTableDataChanged();
	}
	
	public TerminalTableModel() {
		super(UIUtils.getTerminalTable(null), columnNames);
		fireTableDataChanged();
	}
	
	public boolean isCellEditable(int row, int column) {
		return false;
	}
}