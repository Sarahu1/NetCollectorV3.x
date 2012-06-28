/**
 * 
 */
package com.cattsoft.collect.manage.ui;

import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.JTextComponent;

import com.cattsoft.collect.manage.data.MonitorStatus;

/** 监测点状态管理界面.
 * @author ChenXiaohong
 *
 */
public class MonitorStatusOperate extends JDialog {
	private static final long serialVersionUID = 1L;
	/*** 状态数据 */
	private MonitorStatus status;

	public MonitorStatusOperate(Frame parent, MonitorStatus status) {
		super(parent, "监测管理" + ((!status.getName().trim().isEmpty()) ? "("+status.getName()+")" : ""));
		// 模式窗口
		setModal(true);
		this.status = status;
		init();
	}

	public MonitorStatus getStatus() {
		return status;
	}
	
	private void execute(String type) {
		UIUtils.dispatchCloseEvent(this);
		new ShellInfoDialog(status.getAddress(), status.getName(), new String[]{status.getType()}, status.getCatalog(), type);
	}
	
	private void stop() {
		// 停止
		execute(ShellInfoDialog.OperateType.STOP);
	}
	
	private void restart() {
		// 重启
		execute(ShellInfoDialog.OperateType.RESTART);
	}
	
	private void upload() {
		UIUtils.dispatchCloseEvent(this);
		new MonitorFtpConnect(null, status.getAddress(), status.getName(), status.getCatalog());
	}

	private void init() {
		UIUtils.addEscapeListener(this);
		// 关闭行为
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setResizable(false);
		setLayout(null);
		pack();
		setSize(372, 420);

		// 添加显示组件
		addComponent(new JLabel("采集编号:"), new Rectangle(30, 10, 80, 22));
		addJTextComponent(new JTextPane(), status.getMonitor(), new Rectangle(100, 10, 220, 22));

		addComponent(new JLabel("主机名称:"), new Rectangle(30, 40, 80, 22));
		addJTextComponent(new JTextPane(), status.getName(), new Rectangle(100, 40, 220, 22));

		addComponent(new JLabel("主机地址:"), new Rectangle(30, 70, 80, 22));
		addJTextComponent(new JTextPane(), status.getAddress(), new Rectangle(100, 70, 220, 22));

		addComponent(new JLabel("采集类型:"), new Rectangle(30, 100, 80, 22));
		addJTextComponent(
				new JTextPane(),
				"daily".equals(status.getType()) ? "忙时" : ("report"
						.equals(status.getType()) ? "服务" : "常规"),
						new Rectangle(100, 100, 220, 22));

		addComponent(new JLabel("更新时间:"), new Rectangle(30, 130, 80, 22));
		addJTextComponent(new JTextPane(), status.getDate(), new Rectangle(100, 130, 220, 22));

		addComponent(new JLabel("工作目录:"), new Rectangle(30, 160, 80, 22));
		addJTextComponent(new JTextPane(), status.getCatalog(), new Rectangle(100, 160, 220, 22));

		addComponent(new JLabel("版本:"), new Rectangle(30, 190, 80, 22));
		addJTextComponent(new JTextPane(), status.getVersion(), new Rectangle(100, 190, 220, 22));

		addComponent(new JLabel("启动日期:"), new Rectangle(30, 220, 80, 22));

		String start = status.getStart();
		// 格式化程序启动日期
		SimpleDateFormat sdf = new SimpleDateFormat("MMMdd", Locale.US);
		try {
			Date date = sdf.parse(start);
			sdf.applyPattern("MM-dd");
			start = sdf.format(date);
		} catch (ParseException e) {
		}
		addJTextComponent(new JTextPane(), start, new Rectangle(100, 220, 220, 22));

		addComponent(new JLabel("当前命令:"), new Rectangle(30, 250, 80, 22));
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(100, 250, 220, 82);
		JTextPane txtCommand = new JTextPane();
		txtCommand.setEditable(false);
		txtCommand.setText(status.getCommand());
		scrollPane.setViewportView(txtCommand);
		scrollPane.setBorder(null);
		add(scrollPane);

		JButton btnFtp = new JButton("上传");
		btnFtp.setToolTipText("上传文件至监测点");
		btnFtp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				upload();
			}
		});
		addComponent(btnFtp, new Rectangle(80, 350, 62, 24));
		
		JButton btnRestart = new JButton("重启");
		btnRestart.setToolTipText("重新启动采集程序");
		btnRestart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionevent) {
				restart();
			}
		});
		addComponent(btnRestart, new Rectangle(152, 350, 62, 24));

		JButton btnStop = new JButton("停止");
		btnStop.setToolTipText("停止采集程序");
		btnStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionevent) {
				stop();
			}
		});
		addComponent(btnStop, new Rectangle(224, 350, 62, 24));

		UIUtils.setCenter(this);

		setVisible(true);
	}

	private void addJTextComponent(JTextComponent component, String value, Rectangle rec) {
		component.setText(value);
		component.setEditable(false);
		component.setBounds(rec);
		add(component);
	}

	private void addComponent(JComponent comp, Rectangle rec) {
		comp.setBounds(rec);
		add(comp);
	}
}
