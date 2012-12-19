package org.hidetake.gradle.ssh.internal

import net.schmizz.sshj.connection.channel.direct.Session

import org.hidetake.gradle.ssh.api.OperationEventListener
import org.hidetake.gradle.ssh.api.OperationHandler
import org.hidetake.gradle.ssh.api.Remote
import org.hidetake.gradle.ssh.api.SessionSpec

import com.jcraft.jsch.ChannelSftp

/**
 * Default implementation of {@link OperationHandler}.
 * 
 * @author hidetake.org
 *
 */
class DefaultOperationHandler implements OperationHandler {
	protected final SessionSpec spec
	protected final Session session

	/**
	 * Event listeners.
	 */
	final List<OperationEventListener> listeners = []

	/**
	 * Constructor.
	 * 
	 * @param spec
	 * @param session
	 */
	DefaultOperationHandler(SessionSpec spec, Session session) {
		this.spec = spec
		this.session = session
	}

	@Override
	Remote getRemote() {
		spec.remote
	}

	@Override
	void execute(String command) {
		execute([:], command)
	}

	@Override
	void execute(Map options, String command) {
		listeners*.beginOperation('execute', options, command)
		def sshCommand = session.exec(command)
		listeners*.managedChannelConnected(sshCommand, spec)
		sshCommand.join()
		listeners*.managedChannelClosed(sshCommand, spec)
	}

	@Override
	void executeBackground(String command) {
		executeBackground([:], command)
	}

	@Override
	void executeBackground(Map options, String command) {
		throw new IllegalStateException('not implemented yet')
	}

	@Override
	void get(String remote, String local) {
		get([:], remote, local)
	}

	@Override
	void get(Map options, String remote, String local) {
		// FIXME
		listeners*.beginOperation('get', remote, local)
		ChannelSftp channel = session.openChannel('sftp')
		options.each { k, v -> channel[k] = v }
		try {
			channel.connect()
			listeners*.managedChannelConnected(channel, spec)
			channel.get(remote, local)
			listeners*.managedChannelClosed(channel, spec)
		} finally {
			channel.disconnect()
		}
	}

	@Override
	void put(String local, String remote) {
		put([:], local, remote)
	}

	@Override
	void put(Map options, String local, String remote) {
		// FIXME
		listeners*.beginOperation('put', remote, local)
		ChannelSftp channel = session.openChannel('sftp')
		options.each { k, v -> channel[k] = v }
		try {
			channel.connect()
			listeners*.managedChannelConnected(channel, spec)
			channel.put(local, remote, ChannelSftp.OVERWRITE)
			listeners*.managedChannelClosed(channel, spec)
		} finally {
			channel.disconnect()
		}
	}
}
