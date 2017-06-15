package import_catalogue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import catalogue.Catalogue;
import catalogue_browser_dao.TermDAO;
import catalogue_object.Term;
import catalogue_object.TermBuilder;
import naming_convention.Headers;
import open_xml_reader.ResultDataSet;
import term_code_generator.CodeGenerator;

/**
 * Importer of the term sheet. This class can be also used to
 * append new terms by giving them a code which is formed by
 * {@link CodeGenerator#TEMP_TERM_CODE} + whatever we want.
 * If a new term is encountered, its new code will be inserted
 * in {@link #newCodes}, which is basically a function which maps
 * the temporary code in the real code, that was created by
 * {@link CodeGenerator#getTermCode(String)}. This hashmap
 * needs to be passed to the {@link ParentImporter} class
 * to manage the append also regarding parent terms and
 * applicabilities (since a new term could have a parent term
 * which is another new term itself!)
 * @author avonva
 *
 */
public class TermSheetImporter extends SheetImporter<Term> {

	private Catalogue catalogue;
	
	// list of new codes which are created if
	// a TEMP code is found in the import
	private HashMap<String, String> newCodes;
	private Collection<Term> tempTerms;
	
	/**
	 * Initialize the term sheet importer
	 * @param catalogue the catalogue which contains the terms
	 * @param termData the sheet term data
	 */
	public TermSheetImporter( Catalogue catalogue ) {
		this.catalogue = catalogue;
		this.newCodes = new HashMap<>();
		this.tempTerms = new ArrayList<>();
	}
	
	@Override
	public Term getByResultSet(ResultDataSet rs) {

		// skip if no term code
		if ( rs.getString ( Headers.TERM_CODE ).isEmpty() ) {
			System.err.println( "Empty code found, skipping this term" );
			return null;
		}

		// save the code in order to be able to use it later
		// for retrieving terms ids
		String code = rs.getString ( Headers.TERM_CODE );
		
		TermBuilder builder = new TermBuilder();

		builder.setCatalogue( catalogue );
		builder.setCode( code );
		builder.setName( rs.getString ( Headers.TERM_EXT_NAME ) );
		builder.setLabel( rs.getString ( Headers.TERM_SHORT_NAME ) );
		builder.setScopenotes( rs.getString ( Headers.TERM_SCOPENOTE ) );
		builder.setDeprecated( rs.getBoolean ( Headers.DEPRECATED, false ) );
		builder.setLastUpdate( rs.getTimestamp ( Headers.LAST_UPDATE, true ) );
		builder.setValidFrom( rs.getTimestamp ( Headers.VALID_FROM, true ) );
		builder.setValidTo( rs.getTimestamp( Headers.VALID_TO, true ) );
		builder.setStatus( rs.getString( Headers.STATUS ) );

		Term term = builder.build();
		
		// if we have a temp term we save it but we 
		// don't insert it yet, we need to wait all the
		// others terms first, in order to generate a correct
		// term code using the term code mask!
		if ( CodeGenerator.isTempCode( code ) ) {
			
			// save the temp term
			tempTerms.add( term );
			
			// avoid the insert of this term
			return null;
		}
		
		// if standard term, add it and insert it
		return term;
	}

	@Override
	public void insert( Collection<Term> terms ) {

		TermDAO termDao = new TermDAO( catalogue );

		// insert the batch of terms into the db
		termDao.insertTerms( terms );
	}

	@Override
	public Collection<Term> getAllByResultSet(ResultDataSet rs) {
		return null;
	}
	
	/**
	 * Get an hashmap which maps a temporary code, which is, a
	 * code formed by {@link CodeGenerator#TEMP_TERM_CODE}
	 * plus whatever we want, into the real term code which
	 * was created in this class by {@link #importSheet()}.
	 * @return an hashmap, key = temp_code, value = real_code
	 */
	public HashMap<String, String> getNewCodes() {
		return newCodes;
	}

	@Override
	public void end() {

		// for each temp term
		for ( Term newTerm : tempTerms ) {

			// create a new term code following the catalogue
			// term code mask (since we are creating terms
			// automatically, a term code mask needs to be
			// defined! Otherwise we cannot do the append)
			String newCode = CodeGenerator.getTermCode( catalogue.getTermCodeMask() );
			String tempCode = newTerm.getCode();
			
			// add the new code to the hash map to maintain
			// the memory of which temp term code is related
			// to which real code
			newCodes.put( tempCode, newCode );
			
			// update the term code with the real one
			newTerm.setCode( newCode );
			
			// we need a collection since the insert uses
			// only collections
			Collection<Term> term = new ArrayList<>();
			term.add( newTerm );
			
			// insert the current term (we need to insert
			// one term at a time, otherwise new term codes
			// which are based on the database will be always
			// equal among them)
			insert( term );
		}
	}
}
