/*-
 * ========================LICENSE_START=================================
 * IDS Communication Protocol
 * %%
 * Copyright (C) 2017 - 2018 Fraunhofer AISEC
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package de.fhg.ids.comm.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import de.fhg.aisec.ids.api.conm.AttestationResult;

public class IdscpServer {
	private ServerConfiguration config = new ServerConfiguration();
	private Server server;
	private SocketListener sockerListener;
	
	
	public IdscpServer config(ServerConfiguration config) {
		this.config = config;
		return this;
	}
	
	public IdscpServer setSocketListener(SocketListener socketListener) {
		this.sockerListener = socketListener;
		return this;
	}
	
	public IdscpServer start() {
		Server s = new Server(this.config.port);

		// Setup the basic application "context" for this application at "/"
		// This is also known as the handler tree (in jetty speak)
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		s.setHandler(context);

		// Add a websocket to a specific path spec
		ServletHolder holderEvents = new ServletHolder("ids", new ServerSocketServlet(this.config, this.sockerListener));
		context.addServlet(holderEvents, "/");

		try {
			s.start();
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
		this.server = s;
		return this;
	}
	
	public Server getServer() {
		return this.server;
	}
	
	public boolean isRunning() {
		Server s = this.server;
		return s != null && s.isRunning();
	}
}
