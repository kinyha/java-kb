package pay

fun ownerLength(repo: Accounts, id: String): Int {
    val owner: String = repo.findOwner(id)          // теперь это String?, а не String!
    return owner.length
}

fun currencyOf(repo: Accounts): String? = repo.currency(null)   // параметр не-null по @NullMarked
