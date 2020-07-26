package dev.ngocta.pycharm.odoo.xml.dom;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import dev.ngocta.pycharm.odoo.data.OdooRecord;
import dev.ngocta.pycharm.odoo.data.OdooRecordExtraInfo;
import dev.ngocta.pycharm.odoo.python.module.OdooModule;
import org.jetbrains.annotations.Nullable;

public interface OdooDomRecordLike extends OdooDomElement {
    @Attribute("id")
    @Required
    GenericAttributeValue<String> getIdAttribute();

    @Nullable
    default String getId() {
        return getIdAttribute().getValue();
    }

    @Nullable
    default String getModel() {
        return null;
    }

    @Nullable
    default OdooRecordExtraInfo getRecordExtraInfo() {
        return null;
    }

    @Nullable
    default OdooRecord getRecord() {
        if (getId() == null) {
            return null;
        }
        OdooModule module = getOdooModule();
        if (module != null) {
            VirtualFile file = getFile().getVirtualFile();
            return new OdooRecord(getId(), StringUtil.notNullize(getModel()), module.getName(), getRecordExtraInfo(), file);
        }
        return null;
    }
}
