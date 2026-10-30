package ru.otr.ufos.installer.gui;

import org.jetbrains.annotations.NotNull;
import ru.otr.ufos.installer.core.InstallerContext;
import ru.otr.ufos.installer.core.Scenario;
import ru.otr.ufos.installer.core.UserPrompt;
import ru.otr.ufos.installer.logging.InstallerLogger;
import ru.otr.ufos.installer.scenarios.ScenariosUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static ru.otr.ufos.installer.logging.InstallerLogger.LINE_SEPARATOR;
import static ru.otr.ufos.installer.logging.InstallerLogger.LOGGER;

/**
 * GUI инсталлятора УФОС
 */
@SuppressWarnings("DuplicatedCode")
public class InstallerFrame extends JFrame {

	// region Поля
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
	private final JCheckBox usePsqlCheckBox;
	private final JCheckBox forceCheckBox;
	private final JCheckBox verboseCheckBox;
	private final JCheckBox needBackupCheckBox;

	private final JPanel dbPanel;
	private final JTextField dbHostField;
	private final JTextField dbPortField;
	private final JTextField dbNameField;
	private final JTextField dbUserField;
	private final JPasswordField dbPassField;

	private final JTextArea logArea;
	private final JButton runButton;
	private final JProgressBar progressBar;
	// endregion Поля

	public InstallerFrame() {
		setTitle("UFOS Stand Installer");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1000, 1000);
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
				Scenario.INIT.toString(),
				Scenario.UPDATE_CORE.toString()
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
		usePsqlCheckBox = new JCheckBox("Выполнять init SQL через psql", true);
		usePsqlCheckBox.setToolTipText(
				"<html>psql требуется для дампов с 'COPY ... FROM stdin;'.<br>" +
				"Снимите флажок для выполнения через JDBC (например, для H2).</html>"
		);
		forceCheckBox = new JCheckBox("--force");
		verboseCheckBox = new JCheckBox("Подробный вывод");
		needBackupCheckBox = new JCheckBox("Создавать бэкап");
		needBackupCheckBox.setSelected(true);
		needBackupCheckBox.setToolTipText("Создавать бэкап стенда перед обновлением. Снимите флажок, чтобы обновлять без бэкапа");

		checkBoxPanel.add(skipDbCheckBox);
		checkBoxPanel.add(usePsqlCheckBox);
		checkBoxPanel.add(forceCheckBox);
		checkBoxPanel.add(verboseCheckBox);
		checkBoxPanel.add(needBackupCheckBox);

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
		skipDbCheckBox.addItemListener(e -> updateVisibility());
		updateVisibility();

		add(topPanel, BorderLayout.NORTH);

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

	private @NotNull JPanel createDbPanel() {
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
		dbPanel.setVisible(initMode && !skipDbCheckBox.isSelected());

		revalidate();
		repaint();
	}

	private @NotNull JTextField addPathField(@NotNull JPanel panel,
											 @NotNull GridBagConstraints gbc,
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

	private @NotNull JTextField addTextField(@NotNull JPanel panel,
											 @NotNull GridBagConstraints gbc,
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
		if (standDirField.getText().trim().isEmpty()) {
			JOptionPane.showMessageDialog(
					this,
					"Укажите папку стенда",
					"Ошибка",
					JOptionPane.ERROR_MESSAGE
			);
			return;
		}

		runButton.setEnabled(false);
		logArea.setText(EMPTY);
		progressBar.setIndeterminate(true);
		progressBar.setString("Выполняется...");

		// Инициализация общего логгера
		InstallerLogger.init(Path.of("installer-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log").toAbsolutePath(), verboseCheckBox.isSelected());
		LOGGER.addListener((level, message) -> SwingUtilities.invokeLater(() -> {
			logArea.append(message + LINE_SEPARATOR);
			logArea.setCaretPosition(logArea.getDocument().getLength());
		}));

		new Thread(() -> {
			try {
				InstallerContext context = createContext();

				LOGGER.info("==================================================");
				LOGGER.info("Запуск инсталлятора из GUI");
				LOGGER.info("Режим: " + context.getScenario().getDescription());
				LOGGER.info("Стенд: " + context.getStandDir());
				LOGGER.info("Артефакты: " + context.getArtifactsDir());
				LOGGER.info("Бэкапы: " + context.getBackupDir());
				LOGGER.info("==================================================");

				if (context.isDryRun()) {
					ScenariosUtils.showDryRun(context);
					return;
				}

				// Выполнение
				ScenariosUtils.execute(context.getScenario(), context);

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
				LOGGER.error("Ошибка выполнения", ex);

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

	private @NotNull InstallerContext createContext() {
		InstallerContext context = new InstallerContext(getSelectedScenario(), createUserPrompt(InstallerFrame.this));

		context.setStandDir(Paths.get(standDirField.getText().trim()).toAbsolutePath().normalize());
		context.setArtifactsDir(Paths.get(artifactsDirField.getText().trim().isBlank() ? "./artifacts" : artifactsDirField.getText().trim()).toAbsolutePath().normalize());
		context.setBackupDir(context.getStandDir().getParent() == null
				? Path.of("backups").resolve(context.getStandDir().getFileName())
				: context.getStandDir().getParent().resolve("backups").resolve(context.getStandDir().getFileName()));

		context.setCoreWar(getPath(coreWarField.getText()));
		context.setLibsZip(getPath(libsZipField.getText()));
		context.setAppZip(getPath(appZipField.getText()));
		context.setJettyTemplate(getPath(jettyTemplateField.getText()));
		context.setInitSql(getPath(initSqlField.getText()));

		context.setUfosVersion(emptyToNull(ufosVersionField.getText()));
		context.setChownUser(emptyToNull(chownUserField.getText()));

		context.setSkipDb(skipDbCheckBox.isSelected());
		context.setNeedBackup(needBackupCheckBox.isSelected());
		context.setUsePsql(usePsqlCheckBox.isSelected());
		context.setForce(forceCheckBox.isSelected());
		context.setVerbose(verboseCheckBox.isSelected());

		if (context.getScenario() == Scenario.INIT) {
			context.setDbHost(dbHostField.getText().trim());
			context.setDbPort(dbPortField.getText().trim());
			context.setDbName(dbNameField.getText().trim());
			context.setDbUser(dbUserField.getText().trim());
			context.setDbPassword(new String(dbPassField.getPassword()));
		}
		return context;
	}

	private Scenario getSelectedScenario() {
		int index = modeCombo.getSelectedIndex();
		if (0 == index) {
			return Scenario.INIT;
		}
		if (1 == index) {
			return Scenario.UPDATE_CORE;
		}
		return Scenario.INIT;
	}

	private static @NotNull UserPrompt createUserPrompt(InstallerFrame frame) {
		return question -> {
			final boolean[] answer = new boolean[1];
			try {
				SwingUtilities.invokeAndWait(() -> {
					int result = JOptionPane.showConfirmDialog(frame, question, "Подтверждение", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
					answer[0] = result == JOptionPane.YES_OPTION;
				});
			} catch (Exception e) {
				answer[0] = false;
			}

			return answer[0];
		};
	}

	private Path getPath(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return Paths.get(text.trim()).toAbsolutePath().normalize();
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
