/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2009, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.Charset;

import ch.qos.logback.core.util.FileUtil;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.WarnStatus;

/**
 * FileAppender appends log events to a file.
 * 
 * For more information about this appender, please refer to the online manual
 * at http://logback.qos.ch/manual/appenders.html#FileAppender
 * 
 * @author Ceki G&uuml;lc&uuml;
 */
public class FileAppender<E> /*extends WriterAppender<E>*/ extends UnsynchronizedAppenderBase<E> {
  private boolean immediateFlush;
  private OutputStream outputStream;
  private String encoding;
  private Charset charset;

  @Override
  protected void append(E eventObject) {
	  if (!isStarted()) {
	    return;
	  }

	  subAppend(eventObject);
	}

  /**
   * Actual writing occurs here.
   * <p>
   * Most subclasses of <code>WriterAppender</code> will need to override this
   * method.
   *
   * @since 0.9.0
   */
  protected void subAppend(E event) {
    if (!isStarted()) {
      return;
    }

    try {
      String output = this.layout.doLayout(event);
      synchronized (this) {
        writerWrite(output, this.immediateFlush);
      }
    } catch (IOException ioe) {
      // as soon as an exception occurs, move to non-started state
      // and add a single ErrorStatus to the SM.
      this.started = false;
      addStatus(new ErrorStatus("IO failure in appender", this, ioe));
    }
  }

  /**
   * Append to or truncate the file? The default value for this variable is
   * <code>true</code>, meaning that by default a <code>FileAppender</code>
   * will append to an existing file and not truncate it.
   */
  protected boolean append = true;

  /**
   * The name of the active log file.
   */
  protected String fileName = null;

  /**
   * Do we do bufferedIO?
   */
  protected boolean bufferedIO = false;

  /**
   * The size of the IO buffer. Default is 8K.
   */
  protected int bufferSize = 8 * 1024;

  private boolean prudent = false;

  private FileChannel fileChannel = null;

  /**
   * As in most cases, the default constructor does nothing.
   */
  public FileAppender() {
  }

  /**
   * The <b>File</b> property takes a string value which should be the name of
   * the file to append to.
   */
  public void setFile(String file) {
    if (file == null) {
      fileName = file;
    } else {
      // Trim spaces from both ends. The users probably does not want
      // trailing spaces in file names.
      String val = file.trim();
      fileName = val;
    }
  }

  /**
   * @deprecated Use isAppend instead
   */
  public boolean getAppend() {
    return append;
  }

  /**
   * Returns the value of the <b>Append</b> property.
   */
  public boolean isAppend() {
    return append;
  }

  /**
   * If the <b>ImmediateFlush</b> option is set to <code>true</code>, the
   * appender will flush at the end of each write. This is the default behavior.
   * If the option is set to <code>false</code>, then the underlying stream
   * can defer writing to physical medium to a later time.
   * <p>
   * Avoiding the flush operation at the end of each append results in a
   * performance gain of 10 to 20 percent. However, there is safety tradeoff
   * involved in skipping flushing. Indeed, when flushing is skipped, then it is
   * likely that the last few log events will not be recorded on disk when the
   * application exits. This is a high price to pay even for a 20% performance
   * gain.
   */
  public void setImmediateFlush(boolean value) {
    immediateFlush = value;
  }

  /**
   * Returns value of the <b>ImmediateFlush</b> option.
   */
  public boolean getImmediateFlush() {
    return immediateFlush;
  }

  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(String value) {
    encoding = value;
  }

  private void setOutputStream(OutputStream os) {
    // close any previously opened writer
    closeOutputStream();

    this.outputStream = os;
    writeHeader();
  }

  /**
   * Close the underlying {@link java.io.OutputStream}.
   */
  protected void closeOutputStream() {
    if (this.outputStream != null) {
      try {
        // before closing we have to output out layout's footer
        writeFooter();
        this.outputStream.close();
        this.outputStream = null;
      } catch (IOException e) {
        addStatus(new ErrorStatus("Could not close OutputStream for FileAppender.",
            this, e));
      }
    }
  }

  /**
   * Binary appenders may overwrite this default implementation.
   */
  protected void writeHeader() {
    if (layout != null && (this.outputStream != null)) {
      try {
        StringBuilder sb = new StringBuilder();
        appendIfNotNull(sb, layout.getFileHeader());
        appendIfNotNull(sb, layout.getPresentationHeader());
        if (sb.length() > 0) {
          sb.append(CoreConstants.LINE_SEPARATOR);
          // If at least one of file header or presentation header were not
          // null, then append a line separator.
          // This should be useful in most cases and should not hurt.
          writerWrite(sb.toString(), true);
        }

      } catch (IOException ioe) {
        this.started = false;
        addStatus(new ErrorStatus("Failed to write header for appender named ["
            + name + "].", this, ioe));
      }
    }
  }

  protected void appendIfNotNull(StringBuilder sb, String s) {
    if (s != null) {
      sb.append(s);
    }
  }

  /**
   * Binary appenders may overwrite this default implementation.
   */
  protected void writeFooter() {
    if (layout != null && this.outputStream != null) {
      try {
        StringBuilder sb = new StringBuilder();
        appendIfNotNull(sb, layout.getPresentationFooter());
        appendIfNotNull(sb, layout.getFileFooter());
        if (sb.length() > 0) {
          writerWrite(sb.toString(), true); // force flush
        }
      } catch (IOException ioe) {
        this.started = false;
        addStatus(new ErrorStatus("Failed to write footer for appender named ["
            + name + "].", this, ioe));
      }
    }
  }

  /**
   * This method is used by derived classes to obtain the raw file property.
   * Regular users should not be calling this method.
   * 
   * @return the value of the file property
   */
  final public String rawFileProperty() {
    return fileName;
  }

  /**
   * Returns the value of the <b>File</b> property.
   * 
   * <p>This method may be overridden by derived classes.
   * 
   */
  public String getFile() {
    return fileName;
  }

  /**
   * If the value of <b>File</b> is not <code>null</code>, then
   * {@link #openFile} is called with the values of <b>File</b> and <b>Append</b>
   * properties.
   */
  public void start() {
    int errors = 0;
    if (getFile() != null) {
      addInfo("File property is set to [" + fileName + "]");

      if (prudent) {
        if (isAppend() == false) {
          setAppend(true);
          addWarn("Setting \"Append\" property to true on account of \"Prudent\" mode");
        }
        if (getImmediateFlush() == false) {
          setImmediateFlush(true);
          addWarn("Setting \"ImmediateFlush\" to true on account of \"Prudent\" mode");
        }

        if (bufferedIO == true) {
          setBufferedIO(false);
          addWarn("Setting \"BufferedIO\" property to false on account of \"Prudent\" mode");
        }
      }

      // In case both bufferedIO and immediateFlush are set, the former
      // takes priority because 'immediateFlush' is set to true by default.
      // If the user explicitly set bufferedIO, then we should follow her
      // directives.
      if (bufferedIO) {
        setImmediateFlush(false);
        addInfo("Setting \"ImmediateFlush\" property to false on account of \"bufferedIO\" property");
      }

      try {
        openFile(getFile());
      } catch (java.io.IOException e) {
        errors++;
        addError("openFile(" + fileName + "," + append + ") call failed.", e);
      }
    } else {
      errors++;
      addError("\"File\" property not set for appender named [" + name + "].");
    }
    if (this.layout == null) {
      addStatus(new ErrorStatus("No layout set for the appender named \""
          + name + "\".", this));
      errors++;
    }

    charset = Charset.defaultCharset(); // initializing with default charset as fallback
    if(this.encoding != null) {
      try {
        charset = Charset.forName(this.encoding);
      } catch (RuntimeException ex) {
        addStatus(new WarnStatus("Could not resolve charset-encoding \""+this.encoding+"\" for the appender named \""
            + name + "\"! Using default charset as fallback.", this, ex));
        // errors++;
      }
    }

    if (this.outputStream == null) {
      addStatus(new ErrorStatus("No outputStream set for the appender named \""
          + name + "\".", this));
      errors++;
    }
    // only error free appenders should be activated
    if (errors == 0) {
      super.start();
    }
  }

  /**
   * <p> Sets and <i>opens</i> the file where the log output will go. The
   * specified file must be writable.
   * 
   * <p> If there was already an opened file, then the previous file is closed
   * first.
   * 
   * <p> <b>Do not use this method directly. To configure a FileAppender or one
   * of its subclasses, set its properties one by one and then call start().</b>
   * 
   * @param file_name
   *                The path to the log file.
   *
   * @throws IOException
   * 
   */
  public synchronized void openFile(String file_name) throws IOException {
    File file = new File(file_name);
    if (FileUtil.mustCreateParentDirectories(file)) {
      boolean result = FileUtil.createMissingParentDirectories(file);
      if (!result) {
        addError("Failed to create parent directories for ["
            + file.getAbsolutePath() + "]");
      }
    }

    FileOutputStream fileOutputStream = new FileOutputStream(file_name, append);
    if (prudent) {
      fileChannel = fileOutputStream.getChannel();
    }
    OutputStream os=fileOutputStream;
    if (bufferedIO) {
      os = new BufferedOutputStream(os, bufferSize);
    }
    setOutputStream(os);
  }

  public boolean isBufferedIO() {
    return bufferedIO;
  }

  public void setBufferedIO(boolean bufferedIO) {
    this.bufferedIO = bufferedIO;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public void setBufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
  }

  /**
   * @see #setPrudent(boolean)
   * 
   * @return true if in prudent mode
   */
  public boolean isPrudent() {
    return prudent;
  }

  /**
   * When prudent is set to true, file appenders from multiple JVMs can safely
   * write to the same file.
   * 
   * @param prudent
   */
  public void setPrudent(boolean prudent) {
    this.prudent = prudent;
  }

  public void setAppend(boolean append) {
    this.append = append;
  }

  final private void safeWrite(String s) throws IOException {
    FileLock fileLock = null;
    try {
      fileLock = fileChannel.lock();
      long position = fileChannel.position();
      long size = fileChannel.size();
      if (size != position) {
        fileChannel.position(size);
      }
      writeBytes(convertToBytes(s), true);
      //super.writerWrite(s, true);
    } finally {
      if (fileLock != null) {
        fileLock.release();
      }
    }
  }

  protected void writerWrite(String s, boolean flush) throws IOException {
    if (prudent && fileChannel != null) {
      safeWrite(s);
    } else {
      writeBytes(convertToBytes(s), flush);
      //super.writerWrite(s, flush);
    }
  }

  protected void writeBytes(byte[] bytes, boolean flush) throws IOException {
    this.outputStream.write(bytes);
    if (flush) {
      this.outputStream.flush();
    }
  }

  private byte[] convertToBytes(String s) {
    return s.getBytes(charset);
  }

}
