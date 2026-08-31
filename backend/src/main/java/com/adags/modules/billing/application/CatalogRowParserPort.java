package com.adags.modules.billing.application;

import java.io.InputStream;
import java.util.List;

// Port. Reads an .xlsx byte stream, returns validated rows, or throws
// InvalidCatalogFileException listing every problem. Kept as a port so
// the domain/application layer imports no Apache POI type — the
// infrastructure adapter (XlsxCatalogRowParser) is the only class that
// depends on POI, same isolation pattern the JDBC repos already follow.
public interface CatalogRowParserPort {

    List<CatalogRow> parse(InputStream xlsx);
}
