/** 
 * Copyright (c) 2009-2011, Regents of the University of Colorado 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 * Neither the name of the University of Colorado at Boulder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE. 
 */

package org.cleartk.ml.opennlp.maxent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.pear.util.FileUtil;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.Feature;
import org.cleartk.ml.Instance;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.DirectoryDataWriterFactory;
import org.cleartk.ml.jar.Train;
import org.cleartk.ml.opennlp.maxent.MaxentStringOutcomeClassifierBuilder;
import org.cleartk.ml.opennlp.maxent.MaxentStringOutcomeDataWriter;
import org.cleartk.ml.util.InstanceFactory;
import org.cleartk.test.util.DefaultTestBase;
import org.junit.Test;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.testing.util.HideOutput;

/**
 * <br>
 * Copyright (c) 2009-2011, Regents of the University of Colorado <br>
 * All rights reserved.
 * 
 * @author Philip Ogren
 * 
 */

public class MaxentStringOutcomeDataWriterTest extends DefaultTestBase {

  public static class Test1Annotator extends CleartkAnnotator<String> {

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
      List<Feature> features = Arrays.asList(
          new Feature("pos", "NN"),
          new Feature("distance", 3.0),
          new Feature("precision", 1.234));
      Instance<String> instance = new Instance<String>("A", features);
      this.dataWriter.write(instance);

      features = Arrays.asList(new Feature("name", "2PO"), new Feature("p's", 2));
      instance = new Instance<String>("B", features);
      this.dataWriter.write(instance);

      instance = new Instance<String>("Z");
      this.dataWriter.write(instance);

      features = Arrays.asList(new Feature("A_B", "AB"));
      instance = new Instance<String>("A", features);
      this.dataWriter.write(instance);
    }
  }

  @Test
  public void test1() throws Exception {
    AnalysisEngine dataWriterAnnotator = AnalysisEngineFactory.createEngine(
        Test1Annotator.class,
        DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
        outputDirectoryName,
        DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
        MaxentStringOutcomeDataWriter.class.getName());

    dataWriterAnnotator.process(jCas);
    dataWriterAnnotator.collectionProcessComplete();

    File trainFile = new MaxentStringOutcomeClassifierBuilder().getTrainingDataFile(this.outputDirectory);
    String[] lines = FileUtil.loadListOfStrings(trainFile);
    assertEquals("A pos_NN distance=3.0 precision=1.234", lines[0]);
    assertEquals("B name_2PO p's=2.0", lines[1]);
    assertEquals("Z null=0.0", lines[2]);
    assertEquals("A A_B_AB", lines[3]);

    // simply train four different models where each one writes over the previous
    HideOutput hider = new HideOutput();
    Train.main(outputDirectoryName, "10", "1");
    hider.restoreOutput();

  }



  public static class Test4Annotator extends CleartkAnnotator<String> {

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
      List<Feature> features = Arrays.asList(
          new Feature("pos", "NN"),
          new Feature("distance", 3.0),
          new Feature("precision", 1.234));
      Instance<String> instance = new Instance<String>(features);
      this.dataWriter.write(instance);
    }

  }

  /**
   * Here we test that an exception is thrown if an instance with no outcome
   */
  @Test
  public void test4() throws Exception {

    HideOutput hider = new HideOutput();

    AnalysisEngine dataWriterAnnotator = AnalysisEngineFactory.createEngine(
        Test4Annotator.class,
        DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
        outputDirectoryName,
        DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
        MaxentStringOutcomeDataWriter.class.getName());

    AnalysisEngineProcessException aepe = null;
    try {
      dataWriterAnnotator.process(jCas);
    } catch (AnalysisEngineProcessException e) {
      aepe = e;
    }
    dataWriterAnnotator.collectionProcessComplete();
    assertNotNull(aepe);
    hider.restoreOutput();

  }

  public static class Test5Annotator extends CleartkAnnotator<String> {

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
      Instance<String> instance = InstanceFactory.createInstance("a", "b c d");
      this.dataWriter.write(instance);
    }
  }

}
