package ru.otr.ufos.installer.gui;

import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.logging.InstallerLogger;
import ru.otr.ufos.installer.scenarios.ScenarioExecutor;
import ru.otr.ufos.installer.scenarios.InitScenario;
import ru.otr.ufos.installer.scenarios.UpdateCoreScenario;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GUI для UFOS Stand Installer
 */
public class InstallerFrame extends JFrame {

	private final JComboBox<String> modeCombo;
	private final JTextField standDirField;
	private final JTextField artifactsDirField;
	private final JTextArea logArea;
	private final JButton runButton;
	private final JButton browseStandButton;
	private final JButton browseArtifactsButton;
	private final JProgressBar progressBar;
	private final JCheckBox verboseCheckBox;

	// DB Fields Panel
	private final JPanel dbPanel;
	private final JTextField dbHostField;
	private final JTextField dbPortField;
	private final JTextField dbNameField;
	private final JTextField dbUserField;
	private final JPasswordField dbPassField;
	private final JTextField initSqlField;
	private final JButton browseInitSqlButton;

	private InstallerLogger logger;

	public InstallerFrame() {
		setTitle("UFOS Stand Installer v1.0");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(800, 700);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(10, 10));

		// Top Panel: Parameters
		JPanel paramsPanel = new JPanel(new GridBagLayout());
		paramsPanel.setBorder(BorderFactory.createTitledBorder("Параметры установки"));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Mode Selection
		gbc.gridx = 0; gbc.gridy = 0;
		paramsPanel.add(new JLabel("Режим:"), gbc);
		gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
		modeCombo = new JComboBox<>(new String[]{
				"Первичная установка (Init)",
				"Обновление ядра (Update Core)",
				"Обновление Jetty (Update Full)"
		});
		paramsPanel.add(modeCombo, gbc);

		// Stand Dir
		gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.gridwidth = 1;
		paramsPanel.add(new JLabel("Папка стенда:"), gbc);
		gbc.gridx = 1; gbc.weightx = 1.0;
		standDirField = new JTextField(30);
		paramsPanel.add(standDirField, gbc);
		gbc.gridx = 2; gbc.weightx = 0;
		browseStandButton = new JButton("...");
		browseStandButton.addActionListener(e -> browseDirectory(standDirField));
		paramsPanel.add(browseStandButton, gbc);

		// Artifacts Dir
		gbc.gridx = 0; gbc.gridy = 2;
		paramsPanel.add(new JLabel("Папка артефактов:"), gbc);
		gbc.gridx = 1; gbc.weightx = 1.0;
		artifactsDirField = new JTextField("./artifacts");
		paramsPanel.add(artifactsDirField, gbc);
		gbc.gridx = 2; gbc.weightx = 0;
		browseArtifactsButton = new JButton("...");
		browseArtifactsButton.addActionListener(e -> browseDirectory(artifactsDirField));
		paramsPanel.add(browseArtifactsButton, gbc);

		// DB Panel (Only visible for Init)
		dbPanel = new JPanel(new GridBagLayout());
		dbPanel.setBorder(BorderFactory.createTitledBorder("Параметры БД (только для Init)"));

		GridBagConstraints dbGbc = new GridBagConstraints();
		dbGbc.insets = new Insets(2, 2, 2, 2);
		dbGbc.anchor = GridBagConstraints.WEST;
		dbGbc.fill = GridBagConstraints.HORIZONTAL;

		dbHostField = new JTextField("localhost", 15);
		dbPortField = new JTextField("5432", 5);
		dbNameField = new JTextField("sufd", 15);
		dbUserField = new JTextField("sufd", 15);
		dbPassField = new JPasswordField(15);
		initSqlField = new JTextField(20);
		browseInitSqlButton = new JButton("...");

		dbGbc.gridx = 0; dbGbc.gridy = 0; dbPanel.add(new JLabel("Host:"), dbGbc);
		dbGbc.gridx = 1; dbPanel.add(dbHostField, dbGbc);
		dbGbc.gridx = 2; dbPanel.add(new JLabel("Port:"), dbGbc);
		dbGbc.gridx = 3; dbPanel.add(dbPortField, dbGbc);

		dbGbc.gridx = 0; dbGbc.gridy = 1; dbPanel.add(new JLabel("DB Name:"), dbGbc);
		dbGbc.gridx = 1; dbPanel.add(dbNameField, dbGbc);
		dbGbc.gridx = 2; dbPanel.add(new JLabel("User:"), dbGbc);
		dbGbc.gridx = 3; dbPanel.add(dbUserField, dbGbc);

		dbGbc.gridx = 0; dbGbc.gridy = 2; dbPanel.add(new JLabel("Password:"), dbGbc);
		dbGbc.gridx = 1; dbGbc.gridwidth = 3; dbPanel.add(dbPassField, dbGbc);

		dbGbc.gridx = 0; dbGbc.gridy = 3; dbGbc.gridwidth = 1;
		dbPanel.add(new JLabel("Init SQL:"), dbGbc);
		dbGbc.gridx = 1; dbGbc.weightx = 1.0;
		dbPanel.add(initSqlField, dbGbc);
		dbGbc.gridx = 2; dbGbc.weightx = 0;
		browseInitSqlButton.addActionListener(e -> browseFile(initSqlField));
		dbPanel.add(browseInitSqlButton, dbGbc);

		gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
		paramsPanel.add(dbPanel, gbc);

		// Toggle DB panel visibility based on mode
		modeCombo.addActionListener(e -> {
			boolean isInit = modeCombo.getSelectedIndex() == 0;
			dbPanel.setVisible(isInit);
			revalidate();
			repaint();
		});
		dbPanel.setVisible(true);

		add(paramsPanel, BorderLayout.NORTH);

		// Center Panel: Log Output
		logArea = new JTextArea();
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		logArea.setEditable(false);
		logArea.setBackground(Color.BLACK);
		logArea.setForeground(Color.GREEN);
		JScrollPane scrollPane = new JScrollPane(logArea);
		scrollPane.setBorder(BorderFactory.createTitledBorder("Лог выполнения"));
		add(scrollPane, BorderLayout.CENTER);

		// Bottom Panel: Progress & Actions
		JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		verboseCheckBox = new JCheckBox("Подробный вывод (verbose)", false);
		controlPanel.add(verboseCheckBox);
		bottomPanel.add(controlPanel, BorderLayout.WEST);

		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		bottomPanel.add(progressBar, BorderLayout.CENTER);

		runButton = new JButton("Запустить");
		runButton.addActionListener(e -> startInstallation());
		bottomPanel.add(runButton, BorderLayout.EAST);

		add(bottomPanel, BorderLayout.SOUTH);
	}

	private void browseDirectory(JTextField targetField) {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (targetField.getText() != null && !targetField.getText().isEmpty()) {
			chooser.setCurrentDirectory(new File(targetField.getText()));
		}
		int result = chooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			targetField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}

	private void browseFile(JTextField targetField) {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (targetField.getText() != null && !targetField.getText().isEmpty()) {
			chooser.setCurrentDirectory(new File(targetField.getText()).getParentFile());
		}
		int result = chooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			targetField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}

	private void startInstallation() {
		String mode = (String) modeCombo.getSelectedItem();
		String standDir = standDirField.getText().trim();
		String artifactsDir = artifactsDirField.getText().trim();

		if (standDir.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Укажите папку стенда!", "Ошибка", JOptionPane.ERROR_MESSAGE);
			return;
		}

		runButton.setEnabled(false);
		logArea.setText("");

		// Создаём логгер
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path logFile = Paths.get("installer-" + timestamp + ".log");
		logger = new InstallerLogger(logFile, verboseCheckBox.isSelected());

		// Подписываемся на логи для GUI
		logger.addListener((level, message) -> {
			SwingUtilities.invokeLater(() -> {
				logArea.append(message + "\n");
				logArea.setCaretPosition(logArea.getDocument().getLength());
			});
		});

		logger.info("=== Запуск инсталлятора ===");
		logger.info("Режим: " + mode);
		logger.info("Стенд: " + standDir);
		logger.info("Артефакты: " + artifactsDir);

		// Запускаем в отдельном потоке
		new Thread(() -> {
			try {
				Scenario scenario = getScenarioFromCombo();
				Path standPath = Paths.get(standDir);
				Path artifactsPath = Paths.get(artifactsDir);
				Path backupPath = standPath.getParent().resolve("backups").resolve(standPath.getFileName());

				InstallerContext context = new InstallerContext(
						scenario, standPath, artifactsPath, backupPath, logger
				);

				// Заполняем параметры для Init
				if (scenario == Scenario.INIT) {
					context.setParameter("db.host", dbHostField.getText());
					context.setParameter("db.port", dbPortField.getText());
					context.setParameter("db.name", dbNameField.getText());
					context.setParameter("db.user", dbUserField.getText());
					context.setParameter("db.password", new String(dbPassField.getPassword()));
					String initSql = initSqlField.getText();
					if (!initSql.isEmpty()) {
						context.setParameter("init.sql", Paths.get(initSql));
					}
				}

				ScenarioExecutor executor = createExecutor(scenario);
				executor.execute(context);

				SwingUtilities.invokeLater(() -> {
					progressBar.setValue(100);
					progressBar.setString("Готово");
					JOptionPane.showMessageDialog(this, "Операция выполнена успешно!", "Успех", JOptionPane.INFORMATION_MESSAGE);
					runButton.setEnabled(true);
				});

			} catch (Exception ex) {
				logger.error("Критическая ошибка", ex);
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(this, "Ошибка: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
					runButton.setEnabled(true);
					progressBar.setValue(0);
					progressBar.setString("Ошибка");
				});
			}
		}).start();
	}

	private Scenario getScenarioFromCombo() {
		int index = modeCombo.getSelectedIndex();
		switch (index) {
			case 0: return Scenario.INIT;
			case 1: return Scenario.UPDATE_CORE;
			case 2: return Scenario.UPDATE_JETTY;
			default: return Scenario.INIT;
		}
	}

	private ScenarioExecutor createExecutor(Scenario scenario) {
		switch (scenario) {
			case INIT:
				return new InitScenario();
			case UPDATE_CORE:
				return new UpdateCoreScenario();
			case UPDATE_JETTY:
				throw new UnsupportedOperationException("Сценарий UPDATE_JETTY ещё не реализован");
			default:
				throw new IllegalArgumentException("Неизвестный сценарий: " + scenario);
		}
	}
}