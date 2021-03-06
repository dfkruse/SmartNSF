/*
 * Copyright 2013, WebGate Consulting AG
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */
package org.openntf.xrest.xsp.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashSet;
import java.util.List;

import javax.faces.FacesException;
import javax.faces.FactoryFinder;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.event.PhaseListener;
import javax.faces.lifecycle.Lifecycle;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openntf.xrest.xsp.exec.ExecutorException;
import org.openntf.xrest.xsp.exec.RouteProcessorExecutor;
import org.openntf.xrest.xsp.exec.RouteProcessorExecutorFactory;
import org.openntf.xrest.xsp.exec.impl.ContextImpl;
import org.openntf.xrest.xsp.exec.output.ExecutorExceptionProcessor;
import org.openntf.xrest.xsp.exec.output.JsonPayloadProcessor;
import org.openntf.xrest.xsp.model.RouteProcessor;
import org.openntf.xrest.xsp.model.Router;
import org.openntf.xrest.xsp.yaml.YamlProducer;

import com.ibm.commons.util.NotImplementedException;
import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.ibm.domino.xsp.module.nsf.NotesContext;
import com.ibm.xsp.application.ApplicationEx;
import com.ibm.xsp.context.FacesContextEx;
import com.ibm.xsp.controller.FacesController;
import com.ibm.xsp.controller.FacesControllerFactoryImpl;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import io.prometheus.client.exporter.common.TextFormat;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class XRestAPIServlet extends HttpServlet {

	private static Lifecycle dummyLifeCycle = new Lifecycle() {
		@Override
		public void render(FacesContext context) throws FacesException {
			throw new NotImplementedException();
		}

		@Override
		public void removePhaseListener(PhaseListener listener) {
			throw new NotImplementedException();
		}

		@Override
		public PhaseListener[] getPhaseListeners() {
			throw new NotImplementedException();
		}

		@Override
		public void execute(FacesContext context) throws FacesException {
			throw new NotImplementedException();
		}

		@Override
		public void addPhaseListener(PhaseListener listener) {
			throw new NotImplementedException();
		}
	};

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private ServletConfig config;
	private FacesContextFactory contextFactory;
	private RouterFactory routerFactory;
	private Histogram histogram;

	public XRestAPIServlet(final RouterFactory routerFactory) {
		this.routerFactory = routerFactory;
	}

	@Override
	public void init(final ServletConfig config) throws ServletException {
		this.config = config;
		contextFactory = (FacesContextFactory) FactoryFinder.getFactory(FactoryFinder.FACES_CONTEXT_FACTORY);
	}

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (routerFactory.hasError()) {
			publishError(req, resp, routerFactory.getError());
			return;
		}
		if (histogram == null) {
			histogram = routerFactory.buildHistogram();
		}
		Timer timer = null;
		Router router = routerFactory.getRouter();
		FacesContext fc = null;
		try {
			if (router.useFacesContext()) {
				fc = initContext(req, resp);
				FacesContextEx exc = (FacesContextEx) fc;
				ApplicationEx ape = exc.getApplicationEx();
				if (ape.getController() == null) {
					FacesController controller = new FacesControllerFactoryImpl().createFacesController(getServletContext());
					controller.init(null);
				}
			}
			String method = req.getMethod();
			String path = req.getPathInfo();
			if (router.isEnableCORS()) {
				processCORSHeaders(req, resp, router, method);
			}
			if (StringUtil.isEmpty(path)) {
				timer = processBuildInCommands(resp, req);
			} else {
				timer = processRouteProcessorBased(req, resp, method, path, fc);
			}
		} catch (ExecutorException ex) {
			try {
				ExecutorExceptionProcessor.INSTANCE.processExecutorException(ex, resp, router.isTrace());
			} catch (Exception e) {
				e.printStackTrace();
			}

		} catch (Exception ex) {
			try {
				ExecutorExceptionProcessor.INSTANCE.processGeneralException(500, ex, resp);
			} catch (Exception e) {
				e.printStackTrace();
			}

		} finally {
			if (router.useFacesContext() && fc != null) {
				releaseContext(fc);
			}
			if (timer != null) {
				timer.observeDuration();
			}
		}
	}

	private void processCORSHeaders(HttpServletRequest req, HttpServletResponse resp, Router router, String method) {
		if ("OPTIONS".equals(method)) {
			resp.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, " + router.getCORSTokenHeader());
		}
		if (router.isCORSAllowCredentials()) {
			resp.addHeader("Access-Control-Allow-Credentials", "true");
		}
		resp.addHeader("Access-Control-Allow-Origin", toColonValue(router.getCORSOrginValue()));
		resp.addHeader("Access-Control-Allow-Methods", toColonValue(router.getCORSAllowMethodValue()));
	}

	private String toColonValue(List<String> corsOrginValue) {
		StringBuilder sb = new StringBuilder();
		if (corsOrginValue.isEmpty()) {
			return "";
		}
		for (String value : corsOrginValue) {
			sb.append(value);
			sb.append(",");
		}
		return sb.substring(0, sb.length() - 1);
	}

	private Timer processBuildInCommands(final HttpServletResponse resp, final HttpServletRequest request) throws JsonException, IOException, ExecutorException {
		Timer timer = null;
		if ("yaml".equals(request.getQueryString())) {
			timer = histogram.labels("yaml", request.getMethod()).startTimer();
			processYamlRequest(resp, request);
			return timer;
		}
		if ("swagger".equals(request.getQueryString())) {
			timer = histogram.labels("swagger", request.getMethod()).startTimer();
			processSwaggerRequest(resp, request);
			return timer;
		}
		if ("login".equals(request.getQueryString())) {
			timer = histogram.labels("login", request.getMethod()).startTimer();
			processLoginRequest(resp, request);
			return timer;
		}
		if ("metrics".equals(request.getQueryString())) {
			processMetricsRequest(resp, request);
			return timer;
		}
		throw new ExecutorException(500, "Path not found", request.getPathInfo(), "SERVLET");
	}

	private void processMetricsRequest(HttpServletResponse resp, HttpServletRequest request) throws IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType(TextFormat.CONTENT_TYPE_004);
		Writer writer = resp.getWriter();
		try {
			TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
			writer.flush();
		} finally {
			writer.close();
		}
	}

	private void processLoginRequest(HttpServletResponse resp, HttpServletRequest request) throws ExecutorException {
		JsonJavaObject loginObject = new JsonJavaObject();
		try {
			NotesContext c = NotesContext.getCurrentUnchecked();
			Session ses = c.getCurrentSession();
			loginObject.put("username", ses.getEffectiveUserName());
			loginObject.put("groups", c.getGroupList());
			loginObject.put("accesslevel", c.getCurrentDatabase().getCurrentAccessLevel());
			loginObject.put("roles", c.getCurrentDatabase().queryAccessRoles(ses.getEffectiveUserName()));
			loginObject.put("email", c.getInetMail());
			JsonPayloadProcessor.INSTANCE.processJsonPayload(loginObject, resp);
			return;
		} catch (Exception ex) {
			throw new ExecutorException(500, "Error during build response object", ex, request.getPathInfo(), "/?login");
		}
	}

	private void processSwaggerRequest(final HttpServletResponse resp, final HttpServletRequest request) throws IOException {
		String path = request.getRequestURL().toString();
		URL url = new URL(path + "?yaml");
		URL urlSwagger = new URL(url.getProtocol(), url.getHost(), url.getPort(), "/xsp/.ibmxspres/.swaggerui/dist/index.html?url=" + url.toExternalForm());
		resp.sendRedirect(urlSwagger.toExternalForm());
	}

	private void processYamlRequest(final HttpServletResponse resp, final HttpServletRequest request) throws JsonException, IOException {
		Router router = routerFactory.getRouter();
		PrintWriter pw = resp.getWriter();
		YamlProducer yamlProducer = new YamlProducer(router, request, pw);
		yamlProducer.processYamlToPrintWriter();
		pw.close();
	}

	private Timer processRouteProcessorBased(final HttpServletRequest req, final HttpServletResponse resp, final String method, final String path, FacesContext fc)
			throws NotesException, IOException, ExecutorException {
		RouteProcessor rp = routerFactory.getRouter().find(method, path);
		ContextImpl context = new ContextImpl();
		if (rp != null) {
			Timer timer = histogram.labels(rp.getRoute(), rp.getMethod()).startTimer();
			NotesContext c = modifiyNotesContext();
			context.addNotesContext(c).addRequest(req).addResponse(resp);
			context.addRouterVariables(rp.extractValuesFromPath(path));
			context.setTrace(routerFactory.getRouter().isTrace());
			context.addFacesContext(fc);
			if (req.getContentLength() > 0 && req.getContentType() != null && req.getContentType().toLowerCase().startsWith("application/json")) {
				try {
					JsonJavaFactory factory = JsonJavaFactory.instanceEx2;
					JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(factory, req.getReader());
					context.addJsonPayload(json);
				} catch (JsonException jE) {
					jE.printStackTrace();
				}
			}
			RouteProcessorExecutor executor = RouteProcessorExecutorFactory.getExecutor(method, path, context, rp);
			executor.execute();
			return timer;
		} else {
			throw new ExecutorException(500, "Path not found", path, "SERVLET");
		}
	}

	private void publishError(final HttpServletRequest req, final HttpServletResponse resp, final Throwable error) {
		error.printStackTrace();

	}

	public void refresh() {
		routerFactory.refresh();
		histogram = routerFactory.buildHistogram();
	}

	public FacesContext initContext(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// Create a temporary FacesContext and make it available
		FacesContext context = contextFactory.getFacesContext(getServletConfig().getServletContext(), request, response, dummyLifeCycle);
		return context;
	}

	public void releaseContext(FacesContext context) throws ServletException, IOException {
		context.release();
	}

	@Override
	public ServletConfig getServletConfig() {
		return config;
	}

	private NotesContext modifiyNotesContext() {
		NotesContext c = NotesContext.getCurrentUnchecked();
		try {
			Field checkedSigners = NotesContext.class.getDeclaredField("checkedSigners");
			checkedSigners.setAccessible(true);
			HashSet<?> signers = (HashSet<?>) checkedSigners.get(c);
			signers.clear();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		c.setSignerSessionRights("WEB-INF/routes.groovy");
		return c;
	}
}
