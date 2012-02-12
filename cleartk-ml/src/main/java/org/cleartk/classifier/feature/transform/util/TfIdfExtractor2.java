/** 
 * Copyright (c) 2012, Regents of the University of Colorado 
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

package org.cleartk.classifier.feature.transform.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.Instance;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.cleartk.classifier.feature.transform.TrainableExtractor_ImplBase;
import org.cleartk.classifier.feature.transform.TransformableFeature;

import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

/**
 * Transforms count features produced by its subextractor into TF*IDF values
 * <p>
 * 
 * <br>
 * Copyright (c) 2012, Regents of the University of Colorado <br>
 * All rights reserved.
 * 
 * @author Lee Becker
 * 
 */
public class TfIdfExtractor2<OUTCOME_T> extends TrainableExtractor_ImplBase<OUTCOME_T> implements
    SimpleFeatureExtractor {

  private SimpleFeatureExtractor subExtractor;

  private boolean isTrained;

  private IDFMap idfMap;

  public TfIdfExtractor2(String name) {
    super(name);
    this.isTrained = false;
    this.idfMap = new IDFMap();
  }

  /**
   * 
   * @param extractor
   *          - This assumes that any extractors passed in will produce counts of some variety
   */
  public TfIdfExtractor2(String name, SimpleFeatureExtractor extractor) {
    super(name);
    this.subExtractor = extractor;
    this.isTrained = false;
    this.idfMap = new IDFMap();
  }

  @Override
  public List<Feature> extract(JCas view, Annotation focusAnnotation)
      throws CleartkExtractorException {

    List<Feature> extracted = this.subExtractor.extract(view, focusAnnotation);
    List<Feature> result = new ArrayList<Feature>();
    if (this.isTrained) {
      // We have trained / loaded a tf*idf model, so now fix up the values
      for (Feature feature : extracted) {
        int tf = (Integer) feature.getValue();
        double tfidf = tf * this.idfMap.getIDF(feature.getName());
        result.add(new Feature("TF-IDF_" + feature.getName(), tfidf));
      }
    } else {
      // We haven't trained this extractor yet, so just mark the existing features
      // for future modification, by creating one mega container feature
      result.add(new TransformableFeature(this.name, extracted));
    }

    return result;
  }

  @Override
  public void train(Iterable<Instance<OUTCOME_T>> instances) throws CleartkExtractorException {

    // Add instance's term frequencies to the global counts
    for (Instance<OUTCOME_T> instance : instances) {

      Set<String> featureNames = new HashSet<String>();
      // Grab the matching tf*idf features from the set of all features in an instance
      for (TransformableFeature tfidfFeature : this.filter(instance.getFeatures())) {
        // tf*idf features contain a list of features, these are actually what get added
        // to our document frequency map
        for (Feature feature : tfidfFeature.getFeatures()) {
          featureNames.add(feature.getName());
        }
      }

      for (String featureName : featureNames) {
        this.idfMap.add(featureName);
      }
      this.idfMap.incTotalDocumentCount();

    }

    this.isTrained = true;
  }

  @Override
  public void save(URI documentFreqDataURI) throws CleartkExtractorException {
    this.idfMap.save(documentFreqDataURI);
  }

  @Override
  public void load(URI documentFreqDataURI) throws CleartkExtractorException {
    this.idfMap.load(documentFreqDataURI);
    this.isTrained = true;
  }

  private static class IDFMap {
    private Multiset<String> documentFreqMap;

    private int totalDocumentCount;

    public IDFMap() {
      this.documentFreqMap = LinkedHashMultiset.create();
      this.totalDocumentCount = 0;
    }

    public void add(String term) {
      this.documentFreqMap.add(term);
    }

    public void incTotalDocumentCount() {
      this.totalDocumentCount++;
    }

    public int getDF(String term) {
      return this.documentFreqMap.count(term);
    }

    public double getIDF(String term) {
      int df = this.getDF(term);
      return Math.log((this.totalDocumentCount + 1) / (df + 1));
    }

    public void save(URI outputURI) {
      File out = new File(outputURI);
      BufferedWriter writer = null;
      try {
        writer = new BufferedWriter(new FileWriter(out));
        writer.append(String.format("#NUM DOCUMENTS\t%d\n", this.totalDocumentCount));
        for (Multiset.Entry<String> entry : this.documentFreqMap.entrySet()) {
          writer.append(String.format("%s\t%d\n", entry.getElement(), entry.getCount()));
        }
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public void load(URI inputURI) {
      File in = new File(inputURI);
      BufferedReader reader = null;
      // this.documentFreqMap = LinkedHashMultiset.create();
      try {
        reader = new BufferedReader(new FileReader(in));
        // First line specifies the number of documents
        String firstLine = reader.readLine();
        String[] keyValuePair = firstLine.split("\\t");
        this.totalDocumentCount = Integer.parseInt(keyValuePair[1]);

        // The rest of the lines are the term counts
        String line = null;
        while ((line = reader.readLine()) != null) {
          String[] termFreqPair = line.split("\\t");
          this.documentFreqMap.add(termFreqPair[0], Integer.parseInt(termFreqPair[1]));
        }

        reader.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

}
