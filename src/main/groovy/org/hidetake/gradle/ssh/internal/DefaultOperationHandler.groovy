package org.hidetake.gradle.ssh.internal

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import org.hidetake.gradle.ssh.api.OperationEventListener
import org.hidetake.gradle.ssh.api.OperationHandler
import org.hidetake.gradle.ssh.api.Remote
import org.hidetake.gradle.ssh.api.SessionSpec

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
    String execute(String command) {
        execute([:], command)
    }

    @Override
    String execute(Map options, String command) {
        listeners*.beginOperation('execute', options, command)

        ChannelExec channel = session.openChannel('exec')
        channel.command = command
        channel.setErrStream(System.err, true)
        options.each { k, v -> channel[k] = v }

        def result = new StringBuilder()
        try {
            channel.connect()
            listeners*.managedChannelConnected(channel, spec)

            def input = channel.getInputStream()
            while (true) {
                result << readCommandResult(input)
                if (channel.closed) {
                    break
                }
                Thread.sleep(500)
            }

            print(result)
            listeners*.managedChannelClosed(channel, spec)
        } finally {
            channel.disconnect()
        }
        result.toString()
    }

    @Override
    String executeSudo(String command) {
        executeSudo([:], command)
    }

    @Override
    String executeSudo(Map options, String command) {
        listeners*.beginOperation('executeSudo', command, options)

        ChannelExec channel = session.openChannel('exec') as ChannelExec
        channel.command = "sudo -S -p '' $command"
        channel.setErrStream(System.err, true)
        options.each { k, v -> channel[k] = v }

        def result = new StringBuilder()
        try {
            channel.connect()
            listeners*.managedChannelConnected(channel, spec)

            def input = channel.getInputStream()
            def out = channel.getOutputStream()

            def sudoPwd = spec.remote.password
            provideSudoPwd(out, sudoPwd)

            while (true) {
                result << filterPassword(readCommandResult(input), sudoPwd)
                if (result.contains("try again")) {
                    throw new RuntimeException("Unable to execute sudo command. Wrong username/password")
                }
                if (channel.closed) {
                    break
                }
                Thread.sleep(500)
            }

            print(result)
            listeners*.managedChannelClosed(channel, spec)
        } finally {
            channel.disconnect()
        }
        result.toString()
    }

    private void provideSudoPwd(out, sudoPwd) {
        out.write(("$sudoPwd\n").getBytes());
        out.flush()
    }

    private String readCommandResult(InputStream input) {
        def str = new StringBuilder()
        def buf = new byte[1024]
        while (input.available() > 0) {
            def readBytes = input.read(buf, 0, 1024)
            if (readBytes < 0) {
                throw new RuntimeException("Unexpected end of stream when reading command result")
            }
            str << new String(buf, 0, readBytes)
        }
        str.toString()
    }

    private String filterPassword(str, pwd) {
        str ? str.readLines().findAll { !it.contains(pwd) }.join("\n").trim() : ""
    }


    @Override
    void executeBackground(String command) {
        executeBackground([:], command)
    }

    @Override
    void executeBackground(Map options, String command) {
        listeners*.beginOperation('executeBackground', command)
        ChannelExec channel = session.openChannel('exec')
        channel.command = command
        channel.inputStream = null
        channel.setOutputStream(System.out, true)
        channel.setErrStream(System.err, true)
        options.each { k, v -> channel[k] = v }
        channel.connect()
        listeners*.unmanagedChannelConnected(channel, spec)
    }

    @Override
    void get(String remote, String local) {
        get([:], remote, local)
    }

    @Override
    void get(Map options, String remote, String local) {
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
