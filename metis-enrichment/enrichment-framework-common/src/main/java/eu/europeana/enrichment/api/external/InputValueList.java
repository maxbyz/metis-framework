/*
 * Copyright 2007-2013 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */
package eu.europeana.enrichment.api.external;

import eu.europeana.metis.utils.InputValue;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * JSON helper class for InputValue class
 * @author Yorgos.Mamakis@ europeana.eu
 *
 */
@JsonSerialize
@XmlRootElement
public class InputValueList {
	private List<InputValue> inputValueList;

	public List<InputValue> getInputValueList() {
		return inputValueList;
	}

	public void setInputValueList(List<InputValue> inputValueList) {
		this.inputValueList = inputValueList;
	}
	
}
