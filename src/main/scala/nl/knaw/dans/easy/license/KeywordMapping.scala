/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.license

// @formatter:off
trait KeywordMapping { val keyword: String }

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

// metadata table
case object MetadataTable                extends KeywordMapping { val keyword = "MetadataTable" }
case object MetadataKey                  extends KeywordMapping { val keyword = "MetadataKey" }
case object MetadataValue                extends KeywordMapping { val keyword = "MetadataValue" }

// file table
case object HasFiles                     extends KeywordMapping { val keyword = "HasFiles" }
case object FileTable                    extends KeywordMapping { val keyword = "FileTable" }
case object FileKey                      extends KeywordMapping { val keyword = "FileKey" }
case object FileValue                    extends KeywordMapping { val keyword = "FileValue" }

// @formatter:on
