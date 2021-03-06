package org.openntf.xrest.designer.editors;

import static org.eclipse.jdt.ui.text.IJavaPartitions.JAVA_CHARACTER;
import static org.eclipse.jdt.ui.text.IJavaPartitions.JAVA_DOC;
import static org.eclipse.jdt.ui.text.IJavaPartitions.JAVA_MULTI_LINE_COMMENT;
import static org.eclipse.jdt.ui.text.IJavaPartitions.JAVA_SINGLE_LINE_COMMENT;
import static org.eclipse.jdt.ui.text.IJavaPartitions.JAVA_STRING;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;

public class GroovyPartitionScanner extends RuleBasedPartitionScanner {

	public final static String GROOVY_MULTILINE_STRINGS = "__groovy_multiline_string"; //$NON-NLS-1$
	/**
	 * Array with legal content types.
	 * 
	 * @since 3.0
	 */
	public final static String[] LEGAL_CONTENT_TYPES = new String[] { JAVA_DOC, JAVA_MULTI_LINE_COMMENT, JAVA_SINGLE_LINE_COMMENT, JAVA_STRING, JAVA_CHARACTER, GROOVY_MULTILINE_STRINGS };

	/**
	 * Detector for empty comments.
	 */
	static class EmptyCommentDetector implements IWordDetector {

		public boolean isWordStart(char c) {
			return (c == '/');
		}

		public boolean isWordPart(char c) {
			return (c == '*' || c == '/');
		}
	}

	static class WordPredicateRule extends WordRule implements IPredicateRule {

		private final IToken fSuccessToken;

		public WordPredicateRule(IToken successToken) {
			super(new EmptyCommentDetector());
			fSuccessToken = successToken;
			addWord("/**/", fSuccessToken); //$NON-NLS-1$
		}

		/*
		 * @see org.eclipse.jface.text.rules.IPredicateRule#evaluate(
		 * ICharacterScanner, boolean)
		 */
		public IToken evaluate(ICharacterScanner scanner, boolean resume) {
			return super.evaluate(scanner);
		}

		/*
		 * @see org.eclipse.jface.text.rules.IPredicateRule#getSuccessToken()
		 */
		public IToken getSuccessToken() {
			return fSuccessToken;
		}
	}

	/**
	 * Creates the partitioner and sets up the appropriate rules.
	 */
	public GroovyPartitionScanner() {
		super();

		List<IRule> rules = createRules(false);

		IPredicateRule[] result = new IPredicateRule[rules.size()];
		rules.toArray(result);
		setPredicateRules(result);
	}

	public static List<IRule> createRules(boolean withColor) {

		Object objComment;
		Object objdComment;
		Object objmString;
		Object objsString;
		Object objsComment;
		objComment = JAVA_MULTI_LINE_COMMENT;
		objmString = GROOVY_MULTILINE_STRINGS;
		objsString = JAVA_STRING;
		objsComment = JAVA_SINGLE_LINE_COMMENT;
		objdComment = JAVA_DOC;

		IToken comment = new Token(objComment);
		IToken mString = new Token(objmString);
		IToken sString = new Token(objsString);
		IToken sComment = new Token(objsComment);
		IToken jdoc = new Token(objdComment);

		List<IRule> rules = new ArrayList<IRule>();

		// Add rule for single line comments.
		rules.add(new EndOfLineRule("//", sComment));

		// Add rule for strings and character constants.
		rules.add(new MultiLineRule("'''", "'''", mString));
		rules.add(new MultiLineRule("\"\"\"", "\"\"\"", mString));
		// GRECLIPSE-1111 do not eagerly match these kinds of multiline strings
		rules.add(new SingleLineRule("\"", "\"", sString, '\\'));
		rules.add(new SingleLineRule("'", "'", sString, '\\'));

		// Add special case word rule.
		rules.add(new WordPredicateRule(comment));

		// Add rule for JavaDoc
		rules.add(new MultiLineRule("/**", "*/", jdoc, (char) 0, true)); //$NON-NLS-1$ //$NON-NLS-2$

		// Add rules for multi-line comments
		rules.add(new MultiLineRule("/*", "*/", comment, (char) 0, true)); //$NON-NLS-1$ //$NON-NLS-2$

		return rules;
	}
}