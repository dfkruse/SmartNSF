package org.openntf.xrest.xsp.model.strategy;

import java.util.ArrayList;
import java.util.List;

import org.openntf.xrest.xsp.exec.Context;
import org.openntf.xrest.xsp.exec.DatabaseProvider;
import org.openntf.xrest.xsp.exec.ExecutorException;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.View;

public class AllByView implements StrategyModel<List<Document>> {

	private String databaseNameValue;
	private String viewNameValue;

	private Database dbAccess;
	private View viewAccess;

	public void databaseName(String dbName) {
		databaseNameValue = dbName;
	}

	public void viewName(String viewName) {
		this.viewNameValue = viewName;
	}

	public String getDatabaseNameValue() {
		return databaseNameValue;
	}

	public void setDatabaseNameValue(String databaseNameValue) {
		this.databaseNameValue = databaseNameValue;
	}

	public String getViewNameValue() {
		return viewNameValue;
	}

	public void setViewNameValue(String viewNameValue) {
		this.viewNameValue = viewNameValue;
	}

	@Override
	public List<Document> getModel(Context context) throws ExecutorException {
		try {
			dbAccess = DatabaseProvider.INSTANCE.getDatabase(databaseNameValue, context.getDatabase(), context.getSession());
			viewAccess = dbAccess.getView(viewNameValue);
			List<Document> docs = new ArrayList<Document>();
			Document docNext = viewAccess.getFirstDocument();
			while (docNext != null) {
				Document docProcess = docNext;
				docNext = viewAccess.getNextDocument(docNext);
				docs.add(docProcess);
			}
			return docs;
		} catch (Exception ex) {
			throw new ExecutorException(500, ex, "", "getmodel");
		}
	}

}