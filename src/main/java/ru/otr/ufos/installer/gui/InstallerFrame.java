package ru.otr.ufos.installer.gui;

import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.logging.InstallerLogger;
import ru.otr.ufos.installer.scenarios.InitScenario;
import ru.otr.ufos.installer.scenarios.ScenarioExecutor;
import ru.otr.ufos.installer.scenarios.UpdateCoreScenario;
import ru.otr.ufos.installer.scenarios.UpdateJettyScenario;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GUI инсталлятора УФОС
 */
public class InstallerFrame extends JFrame {

	private final JComboBox<String> modeCombo;

	private final JTextField standDirField;
	private final JTextField artifactsDirField;

	private final JTextField coreWarField;
	private final JTextField libsZipField;
	private final JTextField appZipField;
	private final JTextField jettyTemplateField;
	private final JTextField initSqlField;

	private final JTextField ufosVersionField;
	private final JTextField chownUserField;

	private final JCheckBox skipDbCheckBox;
	private final JCheckBox forceCheckBox;
	private final JCheckBox verboseCheckBox;

	private final JPanel dbPanel;
	private final JTextField dbHostField;
	private final JTextField dbPortField;
	private final JTextField dbNameField;
	private final JTextField dbUserField;
	private final JPasswordField dbPassField;

	private final JTextArea logArea;
	private final JButton runButton;
	private final JProgressBar progressBar;

	public InstallerFrame() {
		setTitle("UFOS Stand Installer");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1000, 800);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(8, 8));

		JPanel topPanel = new JPanel(new GridBagLayout());
		topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		int row = 0;

		// Режим
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.weightx = 0;
		gbc.gridwidth = 1;
		topPanel.add(new JLabel("Режим:"), gbc);

		modeCombo = new JComboBox<>(new String[]{
				"Первичная установка (init)",
				"Обновление ядра и приклада (update-core)",
				"Обновление с пересозданием Jetty (update-jetty)"
		});

		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.gridwidth = 2;
		topPanel.add(modeCombo, gbc);

		row++;

		// Основные пути
		standDirField = addPathField(topPanel, gbc, row, "Папка стенда:", true);
		row++;

		artifactsDirField = addPathField(topPanel, gbc, row, "Папка артефактов:", true);
		row++;

		coreWarField = addPathField(topPanel, gbc, row, "WAR ядра (опционально):", false);
		row++;

		libsZipField = addPathField(topPanel, gbc, row, "Архив библиотек (опционально):", false);
		row++;

		appZipField = addPathField(topPanel, gbc, row, "Архив приклада (опционально):", false);
		row++;

		jettyTemplateField = addPathField(topPanel, gbc, row, "Шаблон Jetty (zip или папка):", false);
		row++;

		initSqlField = addPathField(topPanel, gbc, row, "Init SQL (опционально):", false);
		row++;

		ufosVersionField = addTextField(topPanel, gbc, row, "Версия УФОС (опционально):");
		row++;

		chownUserField = addTextField(topPanel, gbc, row, "chown пользователь (опционально):");
		row++;

		// Чекбоксы
		JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

		skipDbCheckBox = new JCheckBox("Пропустить БД");
		forceCheckBox = new JCheckBox("--force");
		verboseCheckBox = new JCheckBox("Подробный вывод");

		checkBoxPanel.add(skipDbCheckBox);
		checkBoxPanel.add(forceCheckBox);
		checkBoxPanel.add(verboseCheckBox);

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 3;
		topPanel.add(checkBoxPanel, gbc);

		row++;

		// Панель БД
		dbHostField = new JTextField("localhost", 15);
		dbPortField = new JTextField("5432", 8);
		dbNameField = new JTextField("sufd", 15);
		dbUserField = new JTextField("sufd", 15);
		dbPassField = new JPasswordField(15);
		dbPanel = createDbPanel();

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 3;
		topPanel.add(dbPanel, gbc);

		modeCombo.addActionListener(e -> updateVisibility());
		updateVisibility();

		JScrollPane topScroll = new JScrollPane(topPanel);
		topScroll.setPreferredSize(new Dimension(980, 430));
		topScroll.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		add(topScroll, BorderLayout.NORTH);

		// Лог
		logArea = new JTextArea();
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		logArea.setEditable(false);
		logArea.setBackground(new Color(20, 20, 20));
		logArea.setForeground(new Color(220, 220, 220));

		JScrollPane logScroll = new JScrollPane(logArea);
		logScroll.setBorder(BorderFactory.createTitledBorder("Лог выполнения"));
		add(logScroll, BorderLayout.CENTER);

		// Нижняя панель
		JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setString("Готов к работе");

		runButton = new JButton("Запустить");
		runButton.addActionListener(e -> startInstallation());

		bottomPanel.add(progressBar, BorderLayout.CENTER);
		bottomPanel.add(runButton, BorderLayout.EAST);

		add(bottomPanel, BorderLayout.SOUTH);
	}

	private JPanel createDbPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder("Параметры БД"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(3, 3, 3, 3);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		int row = 0;

		gbc.gridx = 0;
		gbc.gridy = row;
		panel.add(new JLabel("Хост:"), gbc);

		gbc.gridx = 1;
		panel.add(dbHostField, gbc);

		gbc.gridx = 2;
		panel.add(new JLabel("Порт:"), gbc);

		gbc.gridx = 3;
		panel.add(dbPortField, gbc);

		row++;

		gbc.gridx = 0;
		gbc.gridy = row;
		panel.add(new JLabel("База:"), gbc);

		gbc.gridx = 1;
		panel.add(dbNameField, gbc);

		gbc.gridx = 2;
		panel.add(new JLabel("Пользователь:"), gbc);

		gbc.gridx = 3;
		panel.add(dbUserField, gbc);

		row++;

		gbc.gridx = 0;
		gbc.gridy = row;
		panel.add(new JLabel("Пароль:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 3;
		panel.add(dbPassField, gbc);

		return panel;
	}

	private void updateVisibility() {
		boolean initMode = modeCombo.getSelectedIndex() == 0;
		dbPanel.setVisible(initMode);

		revalidate();
		repaint();
	}

	private JTextField addPathField(JPanel panel,
									GridBagConstraints gbc,
									int row,
									String label,
									boolean directoryOnly) {

		JTextField field = new JTextField(40);
		JButton browseButton = new JButton("...");

		browseButton.addActionListener(e -> browsePath(field, directoryOnly));

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.weightx = 0;
		gbc.gridwidth = 1;
		panel.add(new JLabel(label), gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		panel.add(field, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0;
		panel.add(browseButton, gbc);

		return field;
	}

	private JTextField addTextField(JPanel panel,
									GridBagConstraints gbc,
									int row,
									String label) {

		JTextField field = new JTextField(40);

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.weightx = 0;
		gbc.gridwidth = 1;
		panel.add(new JLabel(label), gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.gridwidth = 2;
		panel.add(field, gbc);

		return field;
	}

	private void browsePath(JTextField targetField, boolean directoryOnly) {
		JFileChooser chooser = new JFileChooser();

		if (directoryOnly) {
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		} else {
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		}

		String currentText = targetField.getText();

		if (currentText != null && !currentText.isBlank()) {
			File current = new File(currentText);

			if (current.exists()) {
				chooser.setSelectedFile(current);
			} else if (current.getParentFile() != null && current.getParentFile().exists()) {
				chooser.setCurrentDirectory(current.getParentFile());
			}
		}

		int result = chooser.showOpenDialog(this);

		if (result == JFileChooser.APPROVE_OPTION) {
			targetField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}

	private void startInstallation() {
		String standText = standDirField.getText().trim();

		if (standText.isEmpty()) {
			JOptionPane.showMessageDialog(
					this,
					"Укажите папку стенда",
					"Ошибка",
					JOptionPane.ERROR_MESSAGE
			);
			return;
		}

		String artifactsText = artifactsDirField.getText().trim();

		if (artifactsText.isEmpty()) {
			artifactsText = "./artifacts";
		}

		final Path standDir = Paths.get(standText).toAbsolutePath().normalize();
		final Path artifactsDir = Paths.get(artifactsText).toAbsolutePath().normalize();

		runButton.setEnabled(false);
		logArea.setText("");
		progressBar.setIndeterminate(true);
		progressBar.setString("Выполняется...");

		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path logFile = Path.of("installer-" + timestamp + ".log").toAbsolutePath();

		InstallerLogger logger = new InstallerLogger(logFile, verboseCheckBox.isSelected());

		logger.addListener((level, message) -> SwingUtilities.invokeLater(() -> {
			logArea.append(message + System.lineSeparator());
			logArea.setCaretPosition(logArea.getDocument().getLength());
		}));

		new Thread(() -> {
			try {
				Scenario scenario = getSelectedScenario();

				Path backupDir = standDir.getParent() == null
						? Path.of("backups").resolve(standDir.getFileName())
						: standDir.getParent().resolve("backups").resolve(standDir.getFileName());

				InstallerContext context = new InstallerContext(
						scenario,
						standDir,
						artifactsDir,
						backupDir,
						logger
				);

				context.setUserPrompt(question -> {
					final boolean[] answer = new boolean[1];

					try {
						SwingUtilities.invokeAndWait(() -> {
							int result = JOptionPane.showConfirmDialog(
									InstallerFrame.this,
									question,
									"Подтверждение",
									JOptionPane.YES_NO_OPTION,
									JOptionPane.QUESTION_MESSAGE
							);

							answer[0] = result == JOptionPane.YES_OPTION;
						});
					} catch (Exception e) {
						answer[0] = false;
					}

					return answer[0];
				});

				setPathParameter(context, "artifact.core.war", coreWarField.getText());
				setPathParameter(context, "artifact.libs.zip", libsZipField.getText());
				setPathParameter(context, "artifact.app.zip", appZipField.getText());
				setPathParameter(context, "artifact.jetty.template", jettyTemplateField.getText());
				setPathParameter(context, "artifact.init.sql", initSqlField.getText());

				context.setParameter("ufos.version", emptyToNull(ufosVersionField.getText()));
				context.setParameter("chown.user", emptyToNull(chownUserField.getText()));

				context.setParameter("skip.db", skipDbCheckBox.isSelected());
				context.setParameter("force", forceCheckBox.isSelected());
				context.setParameter("verbose", verboseCheckBox.isSelected());

				if (scenario == Scenario.INIT) {
					context.setParameter("db.host", dbHostField.getText().trim());
					context.setParameter("db.port", dbPortField.getText().trim());
					context.setParameter("db.name", dbNameField.getText().trim());
					context.setParameter("db.user", dbUserField.getText().trim());
					context.setParameter("db.password", new String(dbPassField.getPassword()));
				}

				logger.info("==================================================");
				logger.info("Запуск инсталлятора из GUI");
				logger.info("Режим: " + scenario.getDescription());
				logger.info("Стенд: " + standDir);
				logger.info("Артефакты: " + artifactsDir);
				logger.info("Бэкапы: " + backupDir);
				logger.info("==================================================");

				ScenarioExecutor executor = createExecutor(scenario);
				executor.execute(context);

				SwingUtilities.invokeLater(() -> {
					progressBar.setIndeterminate(false);
					progressBar.setValue(100);
					progressBar.setString("Готово");

					JOptionPane.showMessageDialog(
							InstallerFrame.this,
							"Операция завершена успешно",
							"Успех",
							JOptionPane.INFORMATION_MESSAGE
					);

					runButton.setEnabled(true);
				});

			} catch (Exception ex) {
				logger.error("Ошибка выполнения", ex);

				SwingUtilities.invokeLater(() -> {
					progressBar.setIndeterminate(false);
					progressBar.setValue(0);
					progressBar.setString("Ошибка");

					JOptionPane.showMessageDialog(
							InstallerFrame.this,
							"Ошибка: " + ex.getMessage(),
							"Ошибка",
							JOptionPane.ERROR_MESSAGE
					);

					runButton.setEnabled(true);
				});
			}
		}).start();
	}

	private Scenario getSelectedScenario() {
		int index = modeCombo.getSelectedIndex();

		switch (index) {
			case 0:
				return Scenario.INIT;
			case 1:
				return Scenario.UPDATE_CORE;
			case 2:
				return Scenario.UPDATE_JETTY;
			default:
				return Scenario.INIT;
		}
	}

	private ScenarioExecutor createExecutor(Scenario scenario) {
		switch (scenario) {
			case INIT:
				return new InitScenario();
			case UPDATE_CORE:
				return new UpdateCoreScenario();
			case UPDATE_JETTY:
				return new UpdateJettyScenario();
			default:
				throw new IllegalArgumentException("Неизвестный сценарий: " + scenario);
		}
	}

	private void setPathParameter(InstallerContext context, String key, String text) {
		if (text == null || text.isBlank()) {
			return;
		}

		context.setParameter(key, Paths.get(text.trim()).toAbsolutePath().normalize());
	}

	private String emptyToNull(String value) {
		if (value == null) {
			return null;
		}

		value = value.trim();

		if (value.isEmpty()) {
			return null;
		}

		return value;
	}
}