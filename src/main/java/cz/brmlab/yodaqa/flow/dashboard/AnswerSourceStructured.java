package cz.brmlab.yodaqa.flow.dashboard;

/**
 * An answer source created during structured search (freebase, dbpedia)
 */
public class AnswerSourceStructured extends AnswerSource {
	/* Keep the following in sync with doc/REST-API.md */

	public static final String TYPE_FREEBASE = "freebase";
	public static final String TYPE_DBPEDIA = "dbpedia";

	/** This comes from knowledge base ontology.  That means
	 * a reasonably curated property space. */
	public static final String ORIGIN_ONTOLOGY = "ontology";
	/** This comes from knowledge base raw property.  That means
	 * an autogenerated, noisy property space. */
	public static final String ORIGIN_RAW_PROPERTY = "raw property";
	/** This comes from knowledge base ontology or property
	 * which was specifically targetted based on question type. */
	public static final String ORIGIN_SPECIFIC = "specific property";

	public AnswerSourceStructured(String type, String origin, String URL, String title) {
		/* XXX: Instead of type passed, we should just have specific
		 * sub-classes (e.g. carry a mid for Freebase). */
		super(type, origin, title, URL);
	}
}
