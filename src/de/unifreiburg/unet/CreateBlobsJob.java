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

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.GroupLayout;

public abstract class CreateBlobsJob extends Job {

  private final JPanel _elSizePanel = new JPanel(new BorderLayout());
  protected final JButton _fromImageButton = new JButton("from Image");

  public CreateBlobsJob() {
    super();
  }

  public CreateBlobsJob(JobTableModel model) {
    super(model);
  }

  @Override
  protected void processModelSelectionChange() {
    _elSizePanel.removeAll();
    if (model() != null) {
      _elSizePanel.add(model().elementSizeUmPanel());
      _elSizePanel.setMinimumSize(
          model().elementSizeUmPanel().getMinimumSize());
      _elSizePanel.setMaximumSize(
          new Dimension(
              Integer.MAX_VALUE,
              model().elementSizeUmPanel().getPreferredSize().height));
    }
    super.processModelSelectionChange();
  }

  @Override
  protected void createDialogElements() {

    super.createDialogElements();

    _parametersDialog.setTitle("U-Net Blob Creation");

    JLabel elSizeLabel = new JLabel("Element Size [µm]:");
    _fromImageButton.setToolTipText(
        "Use native image element size for finetuning");

    _horizontalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addComponent(elSizeLabel)
            .addComponent(_elSizePanel)
            .addComponent(_fromImageButton));
    _verticalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(elSizeLabel)
            .addComponent(_elSizePanel)
            .addComponent(_fromImageButton));
  }

};
