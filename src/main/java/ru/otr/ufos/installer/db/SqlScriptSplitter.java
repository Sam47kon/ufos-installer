package ru.otr.ufos.installer.db;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Разбиение SQL-скрипта на отдельные операторы.
 * Учитывает кавычки, комментарии и dollar-quoted блоки.
 */
@UtilityClass
public class SqlScriptSplitter {

	public static @NotNull List<String> split(String sql) {
		List<String> statements = new ArrayList<>();
		if (sql == null || sql.isBlank()) {
			return statements;
		}

		StringBuilder current = new StringBuilder();

		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		boolean inLineComment = false;
		boolean inBlockComment = false;
		String dollarTag = null;

		int i = 0;
		int length = sql.length();
		while (i < length) {
			char c = sql.charAt(i);
			if (inLineComment) {
				current.append(c);
				if (c == '\n') {
					inLineComment = false;
				}
				i++;
				continue;
			}
			if (inBlockComment) {
				current.append(c);
				if (c == '*' && i + 1 < length && sql.charAt(i + 1) == '/') {
					current.append('/');
					i += 2;
					inBlockComment = false;
					continue;
				}
				i++;
				continue;
			}
			if (dollarTag != null) {
				if (sql.startsWith(dollarTag, i)) {
					current.append(dollarTag);
					i += dollarTag.length();
					dollarTag = null;
					continue;
				}
				current.append(c);
				i++;
				continue;
			}
			if (inSingleQuote) {
				current.append(c);
				if (c == '\'') {
					// Экранированная кавычка ''
					if (i + 1 < length && sql.charAt(i + 1) == '\'') {
						current.append('\'');
						i += 2;
						continue;
					}
					inSingleQuote = false;
				}
				i++;
				continue;
			}
			if (inDoubleQuote) {
				current.append(c);

				if (c == '"') {
					inDoubleQuote = false;
				}
				i++;
				continue;
			}

			// Обычный режим
			if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
				inLineComment = true;
				current.append(c);
				i++;
				continue;
			}
			if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
				inBlockComment = true;
				current.append(c);
				current.append('*');
				i += 2;
				continue;
			}
			if (c == '\'') {
				inSingleQuote = true;
				current.append(c);
				i++;
				continue;
			}
			if (c == '"') {
				inDoubleQuote = true;
				current.append(c);
				i++;
				continue;
			}
			if (c == '$') {
				String tag = tryReadDollarTag(sql, i);
				if (tag != null) {
					dollarTag = tag;
					current.append(tag);
					i += tag.length();
					continue;
				}
			}
			if (c == ';') {
				String statement = current.toString().trim();
				if (!statement.isEmpty()) {
					statements.add(statement);
				}
				current.setLength(0);
				i++;
				continue;
			}
			current.append(c);
			i++;
		}

		String last = current.toString().trim();
		if (!last.isEmpty()) {
			statements.add(last);
		}
		return statements;
	}

	private static @Nullable String tryReadDollarTag(@NotNull String sql, int start) {
		if (start >= sql.length() || sql.charAt(start) != '$') {
			return null;
		}

		int end = start + 1;
		while (end < sql.length() && end - start < 128) {
			char c = sql.charAt(end);
			if (c == '$') {
				String tagBody = sql.substring(start + 1, end);
				if (tagBody.isEmpty()) {
					return sql.substring(start, end + 1);
				}
				if (tagBody.matches("[A-Za-z_][A-Za-z0-9_]*")) {
					return sql.substring(start, end + 1);
				}
				return null;
			}
			if (!(Character.isLetterOrDigit(c) || c == '_')) {
				return null;
			}
			end++;
		}
		return null;
	}
}
