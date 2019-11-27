package dev.ngocta.pycharm.odoo.python.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.jetbrains.python.codeInsight.completion.PythonLookupElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import dev.ngocta.pycharm.odoo.python.OdooPyNames;
import dev.ngocta.pycharm.odoo.python.OdooPyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class OdooModelClassType extends UserDataHolderBase implements PyCollectionType {
    private OdooModelClass myClass;
    private OdooRecordSetType myRecordSetType;

    private OdooModelClassType(@NotNull OdooModelClass source, @NotNull OdooRecordSetType recordSetType) {
        myClass = source;
        myRecordSetType = recordSetType;
    }

    public static OdooModelClassType create(@NotNull String model, @NotNull OdooRecordSetType recordSetType, @NotNull Project project) {
        OdooModelClass cls = OdooModelClass.create(model, project);
        return create(cls, recordSetType);
    }

    public static OdooModelClassType create(@NotNull OdooModelClass source, @NotNull OdooRecordSetType recordSetType) {
        ConcurrentMap<OdooRecordSetType, OdooModelClassType> cache = CachedValuesManager.getCachedValue(source, () -> {
            return CachedValueProvider.Result.create(new ConcurrentHashMap<>(), ModificationTracker.NEVER_CHANGED);
        });
        OdooModelClassType classType = cache.get(recordSetType);
        if (classType == null) {
            classType = new OdooModelClassType(source, recordSetType);
            cache.put(recordSetType, classType);
        }
        return classType;
    }

    @Nullable
    public static OdooModelClassType create(@NotNull PyClass source, @NotNull OdooRecordSetType recordSetType) {
        if (source instanceof OdooModelClass) {
            return create(source, recordSetType);
        }
        OdooModelInfo info = OdooModelInfo.readFromClass(source);
        if (info != null) {
            return create(info.getName(), recordSetType, source.getProject());
        }
        return null;
    }

    public OdooRecordSetType getRecordSetType() {
        return myRecordSetType;
    }

    public OdooModelClassType getOneRecordVariant() {
        return OdooModelClassType.create(myClass, OdooRecordSetType.ONE);
    }

    public OdooModelClassType getMultiRecordVariant() {
        return OdooModelClassType.create(myClass, OdooRecordSetType.MULTI);
    }

    @Nullable
    @Override
    public String getClassQName() {
        return myClass.getQualifiedName();
    }

    @NotNull
    @Override
    public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
        List<PyClassLikeType> result = new LinkedList<>();
        for (PyClass cls : myClass.getSuperClasses(context)) {
            if (cls instanceof OdooModelClass) {
                result.add(OdooModelClassType.create(cls, myRecordSetType));
            } else {
                result.add(new PyClassTypeImpl(cls, isDefinition()));
            }
        }
        return result;
    }

    @Nullable
    @Override
    public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                            @Nullable PyExpression location,
                                                            @NotNull AccessDirection direction,
                                                            @NotNull PyResolveContext resolveContext) {
        return resolveMember(name, location, direction, resolveContext, true);
    }

    @Nullable
    @Override
    public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                            @Nullable PyExpression location,
                                                            @NotNull AccessDirection direction,
                                                            @NotNull PyResolveContext resolveContext,
                                                            boolean inherited) {
        if (!inherited) {
            return null;
        }
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        for (PyClass cls : myClass.getAncestorClasses(context)) {
            PsiElement member = OdooPyUtils.findClassMember(name, cls);
            if (member != null) {
                if (member instanceof PyFunction) {
                    member = OdooModelFunction.wrap((PyFunction) member, this);
                }
                return ResolveResultList.to(member);
            }
        }
        List<OdooModelClass> children = myClass.getDelegationChildren(context);
        for (OdooModelClass child : children) {
            PyTargetExpression attr = child.findField(name, context);
            if (attr != null) {
                return ResolveResultList.to(attr);
            }
        }
        return null;
    }

    @Override
    public void visitMembers(@NotNull Processor<PsiElement> processor, boolean inherited,
                             @NotNull TypeEvalContext context) {
        if (inherited) {
            myClass.getAncestorClasses(context).forEach(cls -> {
                cls.processClassLevelDeclarations((element, state) -> {
                    processor.process(element);
                    return true;
                });
            });
        }
    }

    @NotNull
    @Override
    public Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
        Set<String> result = new HashSet<>();
        visitMembers(member -> {
            if (member instanceof PsiNamedElement) {
                result.add(((PsiNamedElement) member).getName());
            }
            return true;
        }, inherited, context);
        return result;
    }

    @Override
    public boolean isValid() {
        return myClass.isValid();
    }

    @Nullable
    @Override
    public PyClassLikeType getMetaClassType(@NotNull TypeEvalContext context, boolean inherited) {
        return null;
    }

    @NotNull
    @Override
    public List<PyClassLikeType> getAncestorTypes(@NotNull TypeEvalContext context) {
        List<PyClassLikeType> result = new LinkedList<>();
        myClass.getAncestorClasses(context).forEach(cls -> {
            result.add(new PyClassTypeImpl(cls, isDefinition()));
        });
        return result;
    }

    @Nullable
    @Override
    public PyType getReturnType(@NotNull TypeEvalContext context) {
        return null;
    }

    @Nullable
    @Override
    public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression pyCallSiteExpression) {
        return null;
    }

    @Override
    public boolean isDefinition() {
        return myRecordSetType == OdooRecordSetType.NONE;
    }

    @NotNull
    @Override
    public PyClassLikeType toInstance() {
        return myRecordSetType != null ? this : OdooModelClassType.create(myClass, OdooRecordSetType.MODEL);
    }

    @NotNull
    @Override
    public PyClassLikeType toClass() {
        return myRecordSetType == null ? this : OdooModelClassType.create(myClass, OdooRecordSetType.NONE);
    }

    @Override
    public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext processingContext) {
        Map<String, Object> names = new LinkedHashMap<>();
        names.put(OdooPyNames.ID, new PythonLookupElement(OdooPyNames.ID, null, "int", false, null, null));
        TypeEvalContext context = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
        visitMembers(member -> {
            if (member instanceof PsiNamedElement) {
                String name = ((PsiNamedElement) member).getName();
                names.putIfAbsent(name, getCompletionLine((PsiNamedElement) member, context));
            }
            return true;
        }, true, context);
        myClass.getDelegationChildren(context).forEach(child -> {
            child.visitClassAttributes(attr -> {
                if (OdooPyUtils.getModelFieldType(attr, context) != null) {
                    names.putIfAbsent(attr.getName(), getCompletionLine(attr, context));
                }
                return true;
            }, true, context);
        });
        return names.values().toArray();
    }

    @Nullable
    private PythonLookupElement getCompletionLine(@NotNull PsiNamedElement element, @NotNull TypeEvalContext context) {
        String name = element.getName();
        if (name != null) {
            String tailText = null;
            String typeText = null;
            if (element instanceof PyTargetExpression) {
                PyType fieldType = OdooPyUtils.getModelFieldType((PyTargetExpression) element, context);
                if (fieldType != null) {
                    typeText = fieldType.getName();
                }
            } else if (element instanceof PyFunction) {
                List<PyCallableParameter> params = ((PyFunction) element).getParameters(context);
                String paramsText = StringUtil.join(params, PyCallableParameter::getName, ", ");
                tailText = "(" + paramsText + ")";
            }
            return new PythonLookupElement(element.getName(), tailText, typeText, false, element.getIcon(Iconable.ICON_FLAG_READ_STATUS), null);
        }
        return null;
    }

    @NotNull
    @Override
    public String getName() {
        return myClass.getName();
    }

    @NotNull
    public Project getProject() {
        return myClass.getProject();
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(String message) {
        if (!isValid()) {
            throw new PsiInvalidElementAccessException(null, myClass.getName() + ": " + message);
        }
    }

    @NotNull
    @Override
    public OdooModelClass getPyClass() {
        return myClass;
    }

    @NotNull
    @Override
    public List<PyType> getElementTypes() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public PyType getIteratedItemType() {
        if (myRecordSetType != OdooRecordSetType.NONE) {
            return getOneRecordVariant();
        }
        return null;
    }

    @Nullable
    public PyType getFieldTypeByPath(@NotNull String path, @NotNull TypeEvalContext context) {
        String[] fieldNames = path.split("\\.");
        return getFieldTypeByPath(Arrays.asList(fieldNames), context);
    }

    @Nullable
    public PyType getFieldTypeByPath(@NotNull List<String> fieldNames, @NotNull TypeEvalContext context) {
        if (fieldNames.isEmpty()) {
            return null;
        }
        PsiFile file = context.getOrigin();
        if (file == null) {
            return null;
        }
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(file);
        PyType intType = builtinCache.getIntType();
        boolean toId = OdooPyNames.ID.equals(fieldNames.get(fieldNames.size() - 1));
        if (toId) {
            fieldNames = fieldNames.subList(0, fieldNames.size() - 1);
            if (fieldNames.isEmpty()) {
                return intType;
            }
        }
        PyTargetExpression field = myClass.findFieldByPath(fieldNames, context);
        if (field != null) {
            PyType fieldType = OdooPyUtils.getModelFieldType(field, context);
            if (toId) {
                if (fieldType instanceof OdooModelClassType) {
                    return intType;
                }
                return null;
            }
            return fieldType;
        }
        return null;
    }
}