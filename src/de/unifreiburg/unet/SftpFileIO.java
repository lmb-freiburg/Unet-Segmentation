/**************************************************************************
 *
 * Copyright (C) 2018 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 **************************************************************************/

package de.unifreiburg.unet;

import ij.IJ;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.IOException;

import java.util.Vector;

public class SftpFileIO {

  private Session _session = null;
  private ChannelSftp _channel = null;
  private ProgressMonitor _pr = null;

/*======================================================================*/
/*!
 *   Create a new SftpFileIO object.
 *
 *   \param session The open SSH session
 *   \param progressMonitor Progress will be reported to the given
 *          ProgressMonitor
 */
/*======================================================================*/
  public SftpFileIO(Session session, ProgressMonitor progressMonitor)
      throws JSchException {
          _session = session;
          _pr = progressMonitor;
          _channel = (ChannelSftp)_session.openChannel("sftp");
          _channel.connect();
        }

/*======================================================================*/
/*!
 *   Remove the file with given path from the remote host via sftp.
 *
 *   \param path The absolute file path on the remote host
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the remote file could not be removed
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 */
/*======================================================================*/
  public void removeFile(String path)
      throws JSchException, IOException, InterruptedException, SftpException {
    _pr.init(0, "", "", 1);
    _pr.count("Removing file '" + _session.getHost() + ":" + path + "'", 0);
    IJ.log(_session.getUserName() + "@" + _session.getHost() + " $ rm \"" +
           path + "\"");
    _channel.rm(path);
    _pr.count(1);
  }

/*======================================================================*/
/*!
 *   Rename the file with given path from the remote host via sftp.
 *
 *   \param oldpath The absolute file path on the remote host
 *   \param newpath The new absolute file path on the remote host
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the remote file could not be removed
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 */
/*======================================================================*/
  public void renameFile(String oldpath, String newpath)
      throws JSchException, IOException, InterruptedException, SftpException {
    _pr.init(0, "", "", 1);
    _pr.count(
        "Renaming file '" + _session.getHost() + ":" + oldpath + "' to '" +
        newpath + "'", 0);
    IJ.log(_session.getUserName() + "@" + _session.getHost() + " $ mv \"" +
           oldpath + "\" \"" + newpath + "\"");
    _channel.rename(oldpath, newpath);
    _pr.count(1);
  }

/*======================================================================*/
/*!
 *   Remove the folder with given path from the remote host via sftp.
 *   The folder must be empty before it can be removed.
 *
 *   \param path The absolute folder path on the remote host
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the remote folder could not be removed
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 */
/*======================================================================*/
  public void removeFolder(String path)
      throws JSchException, IOException, InterruptedException, SftpException {
    _pr.init(0, "", "", 1);
    _pr.count("Removing folder '" + _session.getHost() + ":" + path + "'", 0);
    IJ.log(_session.getUserName() + "@" + _session.getHost() + " $ rmdir \"" +
           path + "\"");
    _channel.rmdir(path);
    _pr.count(1);
  }

/*======================================================================*/
/*!
 *   Upload the given local file to the remote host via sftp. Required
 *   folders on the remote host will be created and returned in reverse
 *   creation order to allow to easily clean up again later.
 *
 *   \param inFile The file to copy on the local host
 *   \param outFileName The absolute file path on the remote host
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the file could not be copied
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 *
 *   \return A Vector containing the absolute paths of created folders on
 *     the remote host. The vector is sorted in reverse creation order to
 *     allow simple forward iteration for removing the folders again
 */
/*======================================================================*/
  public Vector<String> put(File inFile, String outFileName)
      throws JSchException, IOException, InterruptedException, SftpException {

    Vector<String> createdFolders = new Vector<String>();
    String[] folders = outFileName.split("/");
    String currentFolder = "/";
    _channel.cd("/");
    for (int i = 0; i < folders.length - 1; ++i) {
      if (folders[i].length() > 0) {
        try {
          _channel.cd(folders[i]);
          currentFolder += "/" + folders[i];
        }
        catch (SftpException e) {
          _pr.count(
              "Creating folder '" + currentFolder + "/" + folders[i] +
              "' on host '" + _session.getHost() + "'", 0);
          IJ.log(
              _session.getUserName() + "@" + _session.getHost() +
              " $ mkdir \"" + currentFolder + "/" + folders[i] + "\"");
          _channel.mkdir(folders[i]);
          _channel.cd(folders[i]);
          if (!currentFolder.endsWith("/")) currentFolder += "/";
          currentFolder += folders[i];
          createdFolders.add(0, currentFolder);
        }
      }
    }

    _pr.count(
        "Copying '" + inFile.getName() + "' to host '" + _session.getHost() +
        "'", 0);
    IJ.log(
        "$ sftp \"" + inFile.getAbsolutePath() + "\" \"" +
        _session.getUserName() + "@" + _session.getHost() + ":" +
        _session.getPort() + ":" + outFileName + "\"");
    _channel.put(inFile.getAbsolutePath(), outFileName, _pr,
                 ChannelSftp.OVERWRITE);
    if (_pr.canceled()) {
      _channel.rm(outFileName);
      for (int i = 0; i < createdFolders.size(); ++i)
          _channel.rmdir(createdFolders.get(i));
      throw new InterruptedException("Upload canceled by user");
    }
    return createdFolders;
  }

/*======================================================================*/
/*!
 *   Fetch the remote file with given path via sftp. The file will be marked
 *   for deletion (deleteOnExit()) on Java virtual machine shutdown. So if
 *   you want to keep it you have to explicitly remove this flag from the
 *   File after the copy process.
 *
 *   \param inFileName The file to copy on the remote host
 *   \param outFile The File on the local host to create
 *
 *   \exception JSchException if the SSH session is not open
 *   \exception IOException if the file could not be copied
 *   \exception InterruptedException if the user requests thread termination
 *   \exception SftpException if the Sftp connection fails
 */
/*======================================================================*/
  public void get(
      String inFileName, File outFile)
      throws JSchException, IOException, InterruptedException, SftpException {

    Vector<File> createdFolders = new Vector<File>();
    File folder = outFile.getParentFile();
    while (folder != null && !folder.isDirectory()) {
      createdFolders.add(folder);
      folder = folder.getParentFile();
    }
    for (int i = createdFolders.size() - 1; i >= 0; --i) {
      _pr.count("Creating folder '" + createdFolders.get(i) + "'", 0);
      IJ.log("$ mkdir \"" + createdFolders.get(i) + "\"");
      if (!createdFolders.get(i).mkdir())
          throw new IOException(
              "Could not create folder '" +
              createdFolders.get(i).getAbsolutePath() + "'");
      createdFolders.get(i).deleteOnExit();
    }

   _pr.count(
        "Fetching '" + inFileName + "' from host '" + _session.getHost() + "'",
        0);
    IJ.log(
        "$ sftp \"" + _session.getUserName() +
        "@" + _session.getHost() + ":" + _session.getPort() + ":" +
        inFileName + "\" \"" + outFile.getAbsolutePath() + "\"");
    outFile.deleteOnExit();
    _channel.get(inFileName, outFile.getAbsolutePath(), _pr,
                 ChannelSftp.OVERWRITE);
    if (_pr.canceled())
        throw new InterruptedException("Download canceled by user");
  }

}
