package cz.brmlab.yodaqa.pipeline;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.AbstractCas;
import org.apache.uima.fit.component.JCasMultiplier_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCopier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.analysis.answer.AnswerFV;
import cz.brmlab.yodaqa.model.Question.QuestionInfo;
import cz.brmlab.yodaqa.model.SearchResult.ResultInfo;
import cz.brmlab.yodaqa.model.CandidateAnswer.AF_OriginDocTitle;
import cz.brmlab.yodaqa.model.CandidateAnswer.AF_OriginMultiple;
import cz.brmlab.yodaqa.model.CandidateAnswer.AF_OriginPsgNE;
import cz.brmlab.yodaqa.model.CandidateAnswer.AF_OriginPsgNP;
import cz.brmlab.yodaqa.model.CandidateAnswer.AnswerInfo;
import cz.brmlab.yodaqa.model.AnswerHitlist.Answer;

/**
 * Take a set of per-answer CandidateAnswerCAS and merge them to
 * an AnswerHitlistCAS.
 *
 * We also deduplicate answers with identical text. */

public class AnswerMerger extends JCasMultiplier_ImplBase {
	final Logger logger = LoggerFactory.getLogger(AnswerMerger.class);

	/** Number of CASes marked as isLast required to encounter before
	 * the final merging is performed.  When multiple independent CAS
	 * multipliers are generating CASes, they each eventually produce
	 * one with an isLast marker. */
	public static final String PARAM_ISLAST_BARRIER = "islast-barrier";
	@ConfigurationParameter(name = PARAM_ISLAST_BARRIER, mandatory = false, defaultValue = "1")
	protected int isLastBarrier;

	protected class AnswerFeatures {
		Answer answer;
		AnswerFV fv;

		public AnswerFeatures(Answer answer_, AnswerFV fv_) {
			answer = answer_;
			fv = fv_;
		}

		/** * @return the answer */
		public Answer getAnswer() { return answer; }
		/** * @return the fv */
		public AnswerFV getFV() { return fv; }
	}

	Map<String, List<AnswerFeatures>> answersByText;
	JCas finalCas;
	boolean isFirst;
	int isLast;

	protected void reset() {
		answersByText = new HashMap<String, List<AnswerFeatures>>();
		finalCas = null;
		isFirst = true;
		isLast = 0;
	}

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		reset();
	}

	public void process(JCas canCas) throws AnalysisEngineProcessException {
		JCas canQuestion, canAnswer;
		try {
			canQuestion = canCas.getView("Question");
			canAnswer = canCas.getView("Answer");
		} catch (Exception e) {
			throw new AnalysisEngineProcessException(e);
		}

		if (finalCas == null)
			finalCas = getEmptyJCas();

		if (isFirst) {
			QuestionInfo qi = JCasUtil.selectSingle(canQuestion, QuestionInfo.class);
			/* Copy QuestionInfo */
			CasCopier copier = new CasCopier(canQuestion.getCas(), finalCas.getCas());
			QuestionInfo qi2 = (QuestionInfo) copier.copyFs(qi);
			qi2.addToIndexes();
			isFirst = false;
		}

		AnswerInfo ai = JCasUtil.selectSingle(canAnswer, AnswerInfo.class);
		ResultInfo ri = JCasUtil.selectSingle(canAnswer, ResultInfo.class);
		isLast += (ai.getIsLast() && ri.getIsLast() ? 1 : 0);

		if (canAnswer.getDocumentText() == null)
			return; // we received a dummy CAS

		AnswerFV fv = new AnswerFV(ai);
		Answer answer = new Answer(finalCas);
		String text = canAnswer.getDocumentText();
		answer.setText(text);

		// System.err.println("AR process: " + answer.getText());

		List<AnswerFeatures> answers = answersByText.get(text);
		if (answers == null) {
			answers = new LinkedList<AnswerFeatures>();
			answersByText.put(text, answers);
		}
		answers.add(new AnswerFeatures(answer, fv));
	}

	public boolean hasNext() throws AnalysisEngineProcessException {
		return isLast >= isLastBarrier;
	}

	public AbstractCas next() throws AnalysisEngineProcessException {
		if (isLast < isLastBarrier)
			throw new AnalysisEngineProcessException();

		/* Deduplicate Answer objects and index them. */
		for (Entry<String, List<AnswerFeatures>> entry : answersByText.entrySet()) {
			Answer mainAns = null;
			AnswerFV mainFV = null;
			for (AnswerFeatures af : entry.getValue()) {
				Answer answer = af.getAnswer();
				if (mainAns == null) {
					mainAns = answer;
					mainFV = af.getFV();
					continue;
				}
				logger.debug("hitlist merge " + mainAns.getText() + "|" + answer.getText());
				mainFV.merge(af.getFV());
			}

			/* At this point we can generate some features
			 * to be aggregated over all individual answer
			 * instances. */
			if (mainFV.getFeatureValue(AF_OriginPsgNP.class)
			    + mainFV.getFeatureValue(AF_OriginPsgNE.class)
			    + mainFV.getFeatureValue(AF_OriginDocTitle.class) > 1.0)
				mainFV.setFeature(AF_OriginMultiple.class, 1.0);

			mainAns.setFeatures(mainFV.toFSArray(finalCas));
			mainAns.addToIndexes();
		}

		JCas outputCas = finalCas;
		reset();
		return outputCas;
	}
}
