package com.cattsoft.collect.manage.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.cattsoft.collect.manage.logging.LogManager;

public class ExportSetDialog extends JDialog {
	private Logger logger = LogManager.getLogger(getClass().getSimpleName());
	
	/*** SQl语句 */
	private JTextField txtSQL;
	/*** 输出路径 */
	private JTextField txtPath;
	
	private JButton btnConnect;
	private JButton btnClose;
	
	public ExportSetDialog(JFrame parent, String sql, String path) {
		super(parent, "数据导出");
		// 模式窗口
		setModal(true);
		// 居中
		UIUtils.setCenter(this);
		init(sql, path);
	}
	private void init(String sql, String path) {
		// 初始化界面
		setLayout(null);
		UIUtils.addEscapeListener(this);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setResizable(false);
		pack();
		
		setSize(320, 200);
		// 添加组件
		JLabel lblsql = new JLabel("SQL:");
		lblsql.setBounds(49, 30, 60, 24);
		add(lblsql);
		
		JLabel lblpath = new JLabel("路径:");
		lblpath.setBounds(49, 64, 60, 24);
		add(lblpath);
		
		txtSQL = new JTextField(sql);
		txtSQL.setBounds(89, 30, 180, 24);
		//UIUtils.addDefaultValueListener(txtSQL, "select*from ttttt");
		
		txtPath = new JTextField(path);
		txtPath.setBounds(89, 64, 180, 24);
		//UIUtils.addDefaultValueListener(txtPath, "c:/liang");
		
		btnConnect = new JButton("导出");
		btnConnect.setBounds(135, 104, 62, 24);
		btnConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionevent) {
				System.out.println(txtSQL.getText());
				System.out.println(txtPath.getText());
				new Thread(new Runnable(){
					@Override
					public void run() {
						// new ExportManager(txtSQL.getText(),txtPath.getText(),true);
					}
				}).start();
				dispose();
			}
		});
		
		btnClose = new JButton("关闭");
		btnClose.setBounds(208, 104, 62, 24);
		btnClose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionevent) {
				close();
			}
		});
		add(btnConnect);
		add(btnClose);
		add(txtSQL);
		add(txtPath);
	}
	
	public void setVisible(boolean b) {
		if(b) {
			// 填写默认值
			if(txtSQL.getText().trim().isEmpty())
				txtSQL.setText("select substr(url,8) from t_url where linkcount between 50 and 200");
			if(txtPath.getText().trim().isEmpty())
				txtPath.setText("d:/conf/");
			
		}
		super.setVisible(b);
	}

	private void close() {
		UIUtils.dispatchCloseEvent(this);
	}
}
