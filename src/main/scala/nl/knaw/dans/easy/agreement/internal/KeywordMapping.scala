/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.agreement.internal

// @formatter:off
trait KeywordMapping { val keyword: String }

// logos in header and footer (for 64bit encoding)
case object DansLogo                     extends KeywordMapping { val keyword = "DansLogo" }
case object DrivenByData                 extends KeywordMapping { val keyword = "DrivenByData" }

// footer text
case object FooterText                   extends KeywordMapping { val keyword = "FooterText" }

// header data
case object IsSample                     extends KeywordMapping { val keyword = "IsSample" }
case object DansManagedDoi               extends KeywordMapping { val keyword = "DansManagedDoi" }
case object DansManagedEncodedDoi        extends KeywordMapping { val keyword = "DansManagedEncodedDoi" }
case object DateSubmitted                extends KeywordMapping { val keyword = "DateSubmitted" }
case object Title                        extends KeywordMapping { val keyword = "Title" }

// depositor data
case object DepositorName                extends KeywordMapping { val keyword = "Name" }
case object DepositorOrganisation        extends KeywordMapping { val keyword = "Organisation" }
case object DepositorAddress             extends KeywordMapping { val keyword = "Address" }
case object DepositorPostalCode          extends KeywordMapping { val keyword = "PostalCode" }
case object DepositorCity                extends KeywordMapping { val keyword = "City" }
case object DepositorCountry             extends KeywordMapping { val keyword = "Country" }
case object DepositorTelephone           extends KeywordMapping { val keyword = "Telephone" }
case object DepositorEmail               extends KeywordMapping { val keyword = "Email" }

// access rights
case object OpenAccess                   extends KeywordMapping { val keyword = "OpenAccess" }
case object OpenAccessForRegisteredUsers extends KeywordMapping { val keyword = "OpenAccessForRegisteredUsers" }
case object OtherAccess                  extends KeywordMapping { val keyword = "OtherAccess" }
case object RestrictGroup                extends KeywordMapping { val keyword = "RestrictGroup" }
case object RestrictRequest              extends KeywordMapping { val keyword = "RestrictRequest" }

// embargo
case object UnderEmbargo                 extends KeywordMapping { val keyword = "UnderEmbargo" }
case object DateAvailable                extends KeywordMapping { val keyword = "DateAvailable" }

case object CurrentDateAndTime           extends KeywordMapping { val keyword = "CurrentDateAndTime" }
case object TermsLicense                 extends KeywordMapping { val keyword = "TermsLicense" }
case object TermsLicenseUrl              extends KeywordMapping { val keyword = "TermsLicenseUrl" }
case object Appendix3                    extends KeywordMapping { val keyword = "Appendix3" }// @formatter:on
