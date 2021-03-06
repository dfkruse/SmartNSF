router.GET('topics}') {
	strategy(DOCUMENTS_BY_VIEW) {
		viewName ('($all)')
	}
	mapJson "date", json:'date',type:'DATETIME',isformula:true, formula:'@Created'
	mapJson "Subject", json:'topic', type:'STRING'
	mapJson "author", json:'author', type:'STRING',isformula:true,formula:'@Name([CN]; From)'
}
router.GET('topics/{id}') {
	strategy(DOCUMENT_BY_UNID) {
			keyVariableName("{id}")
	}
	mapJson "date", json:'date',type:'DATETIME',isformula:true, formula:'@Created'
	mapJson "Subject", json:'topic', type:'STRING'
	mapJson "author", json:'author', type:'STRING',isformula:true, formula:'@Name([CN]; From)'
	mapJson "body", json:'content', type:'MIME'
	mapJson "categories", json:'categories', type:'ARRAY_OF_STRING'
}
router.GET('topics/{id}/attachment/{attachmentName}') {
	strategy(ATTACHMENT) {
		documentStrategy(DOCUMENT_BY_UNID) {
			keyVariableName("{id}")
		}
		fieldName "Body"
		selectionType BY_NAME //Could be BY_NAME, FIRST
		attachmentNameVariableName "{attachmentName}"
	}
}
router.POST('topics/{id}') {
	strategy(DOCUMENT_BY_UNID) {
		keyVariableName("{id}")
	}
	mapJson "Subject", json:'topic', type:'STRING'
	mapJson "body", json:'content', type:'MIME'
	mapJson "categories", json:'categories', type:'ARRAY_OF_STRING'
	events POST_SAVE_DOCUMENT: {
		context, document ->
		nsfHelp = context.getNSFHelper()
		nsfHelp.computeWithForm(document)
	}
}

router.POST('topics/{id}/attachment') {
	strategy(ATTACHMENT){
		documentStrategy(DOCUMENT_BY_UNID) {
			keyVariableName("{id}")
		}
		fieldName "Body"
		updateType REPLACE_BY_NAME //Could be REPLACE_ALL, REPLACE_BY_NAME
	}
}

router.GET('comments/byparent/{parent_id}') {
	strategy(DOCUMENTS_FROM_VIEW_BY_KEY) {
			keyVariableName("{parent_id}")
			viewName("commentsByParentId")
	}
	mapJson "date", json:'date',type:'DATETIME',isformula:true, formula:'@Created'
	mapJson "Subject", json:'topic', type:'STRING'
	mapJson "author", json:'author', type:'STRING',isformula:true,formula:'@Name([CN]; From)'
}

router.GET('comments/{id}') {
	strategy(DOCUMENT_BY_UNID) {
			keyVariableName("{id}")
	}
	mapJson "date", json:'date',type:'DATETIME',isformula:true, formula:'@Created'
	mapJson "Subject", json:'topic', type:'STRING'
	mapJson "author", json:'author', type:'STRING',isformula:true,formula:'@Name([CN]; From)'
	mapJson "body", json:'content', type:'MIME'
	mapJson "categories", json:'categories', type:'ARRAY_OF_STRING'

}

router.POST('comments/{id}/parent/{parent_id}') {
	strategy(DOCUMENT_BY_UNID) {
		keyVariableName("{id}")
	}
	mapJson "Subject", json:'topic', type:'STRING'
	mapJson "body", json:'content', type:'MIME'
	mapJson "categories", json:'categories', type:'ARRAY_OF_STRING'
	events PRE_SAVE_DOCUMENT:{
		context, document ->
		parentId = context.getRouterVariables().get('parent_id')
		nsfHelp = context.getNSFHelper()
		nsfHelp.makeDocumentAsChild(parentId, document)
	}
}

router.DELETE('document/{id}') {
	strategy(DOCUMENT_BY_UNID) {
		keyVariableName("{id}")
	}
}