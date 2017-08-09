package com.haulmont.yarg.formatters.impl.docx;

import com.haulmont.yarg.formatters.impl.AbstractFormatter;
import com.haulmont.yarg.formatters.impl.DocxFormatterDelegate;
import com.haulmont.yarg.structure.BandData;
import org.docx4j.TraversalUtil;
import org.docx4j.XmlUtils;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

import java.util.regex.Matcher;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * @author degtyarjov
 * @version $Id$
 */
public class TableManager {
    public final AliasVisitor INVARIANTS_SETTER;
    protected DocxFormatterDelegate docxFormatter;
    protected Tbl table;
    protected Tr firstRow = null;
    protected Tr rowWithAliases = null;
    protected String bandName = null;
    protected boolean skipIt = false;

    TableManager(DocxFormatterDelegate docxFormatter, Tbl tbl) {
        this.docxFormatter = docxFormatter;
        this.table = tbl;
        INVARIANTS_SETTER = new AliasVisitor(docxFormatter) {
            @Override
            protected void handle(Text text) {

            }
        };
    }

    public Tr copyRow(Tr row) {
        Tr copiedRow = XmlUtils.deepCopy(row);
        new TraversalUtil(copiedRow, INVARIANTS_SETTER);//set parent for each sub-element of copied row (otherwise parent would be JaxbElement)
        int index = table.getContent().indexOf(row);
        table.getContent().add(index, copiedRow);
        return copiedRow;
    }

    public void fillRowFromBand(Tr row, final BandData band) {
        new TraversalUtil(row, new AliasVisitor(docxFormatter) {
            @Override
            protected void handle(Text text) {
                String textValue = text.getValue();
                if (docxFormatter.containsJustOneAlias(textValue)) {//todo eude not only one value cells?
                    String parameterName = docxFormatter.unwrapParameterName(textValue);
                    String fullParameterName = bandName + "." + parameterName;
                    Object parameterValue = band.getParameterValue(parameterName);

                    if (docxFormatter.tryToApplyInliners(fullParameterName, parameterValue, text)) return;
                }


                //todo eude the following logic is not full and ignores situation when in 1 text we have both table and not table aliases
                boolean hasTableAliases = false;
                Matcher matcher = AbstractFormatter.UNIVERSAL_ALIAS_PATTERN.matcher(textValue);
                while(matcher.find()) {
                    AbstractFormatter.BandPathAndParameterName bandAndParameter = docxFormatter.separateBandNameAndParameterName(matcher.group(1));
                    if (isBlank(bandAndParameter.getBandPath()) || isBlank(bandAndParameter.getParameterName())) {
                        hasTableAliases = true;
                    }
                }

                if (hasTableAliases) {
                    String resultString = docxFormatter.insertBandDataToString(band, textValue);
                    text.setValue(resultString);
                }
                text.setSpace("preserve");
            }

            @Override
            public boolean shouldTraverse(Object o) {
                //ignore nested tables in control bands
                return controlTable() ? !(o instanceof Tbl) : super.shouldTraverse(o);
            }
        });
    }

    public Tbl getTable() {
        return table;
    }

    public Tr getFirstRow() {
        return firstRow;
    }

    public Tr getRowWithAliases() {
        return rowWithAliases;
    }

    public String getBandName() {
        return bandName;
    }

    /**
     * Control table is a specific concept which allows to show or hide parts of document
     * depending on the control table's band values.
     * Control table's band usually has 1 record or none.
     *
     * @return
     */
    public boolean controlTable() {
        //todo eude - try to detect control tables more conveniently
        return bandName.endsWith("Control") && noHeader();
    }

    public boolean noHeader() {
        return getRowWithAliases() != null && getFirstRow().equals(getRowWithAliases());
    }

    public boolean isSkipIt() {
        return skipIt;
    }

    public void setSkipIt(boolean skipIt) {
        this.skipIt = skipIt;
    }
}
