package nl.knaw.dans.easy.license

object DatasetValidator {

  def validate(dataset: Dataset): Dataset = {
    dataset.copy(easyUser = validate(dataset.easyUser))
  }

  def validate(easyUser: EasyUser): EasyUser = {
    easyUser.copy(
      organization = easyUser.organization.emptyIfBlank,
      country = easyUser.country.emptyIfBlank,
      telephone = easyUser.telephone.emptyIfBlank
    )
  }
}
