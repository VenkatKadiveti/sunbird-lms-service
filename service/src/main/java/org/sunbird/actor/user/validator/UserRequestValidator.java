package org.sunbird.actor.user.validator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.exception.ResponseCode;
import org.sunbird.exception.ResponseMessage;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.operations.ActorOperations;
import org.sunbird.request.Request;
import org.sunbird.request.RequestContext;
import org.sunbird.util.DataCacheHandler;
import org.sunbird.util.FormApiUtil;
import org.sunbird.util.ProjectUtil;
import org.sunbird.util.StringFormatter;
import org.sunbird.validator.BaseRequestValidator;

public class UserRequestValidator extends BaseRequestValidator {

  private final int ERROR_CODE = ResponseCode.CLIENT_ERROR.getResponseCode();
  protected static List<String> typeList = new ArrayList<>();
  private static final LoggerUtil logger = new LoggerUtil(UserRequestValidator.class);

  static {
    List<String> subTypeList =
        Arrays.asList(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_VALID_LOCATION_TYPES).split(";"));
    for (String str : subTypeList) {
      typeList.addAll(
          ((Arrays.asList(str.split(",")))
                  .stream()
                  .map(
                      x -> {
                        return x.toLowerCase();
                      }))
              .collect(Collectors.toList()));
    }
  }

  public void validateCreateUserRequest(Request userRequest) {
    externalIdsValidation(userRequest, JsonKey.CREATE);
    fieldsNotAllowed(
        Arrays.asList(
            JsonKey.REGISTERED_ORG_ID,
            JsonKey.ROOT_ORG_ID,
            JsonKey.PROVIDER,
            JsonKey.EXTERNAL_ID,
            JsonKey.EXTERNAL_ID_PROVIDER,
            JsonKey.EXTERNAL_ID_TYPE,
            JsonKey.ID_TYPE,
            JsonKey.PROFILE_USERTYPES),
        userRequest);
    createUserBasicValidation(userRequest);
    validateUserType(userRequest.getRequest(), null, userRequest.getRequestContext());
    phoneValidation(userRequest);
    validatePassword((String) userRequest.getRequest().get(JsonKey.PASSWORD));
  }

  public static boolean isGoodPassword(String password) {
    return password.matches(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_PASS_REGEX));
  }

  private void validatePassword(String password) {
    if (StringUtils.isNotBlank(password)) {
      boolean response = isGoodPassword(password);
      if (!response) {
        throw new ProjectCommonException(
            ResponseCode.passwordValidation.getErrorCode(),
            ResponseCode.passwordValidation.getErrorMessage(),
            ERROR_CODE);
      }
    }
  }

  /**
   * This method will validate location type
   *
   * @param type
   * @return
   */
  public static boolean isValidLocationType(String type) {
    if (null != type && !typeList.contains(type.toLowerCase())) {
      throw new ProjectCommonException(
          ResponseCode.invalidValue.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseCode.invalidValue.getErrorMessage(), JsonKey.LOCATION_TYPE, type, typeList),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    return true;
  }

  private void validateUserName(Request userRequest) {
    validateParam(
        (String) userRequest.getRequest().get(JsonKey.USERNAME),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.USERNAME);
  }

  public void validateUserCreateV3(Request userRequest) {
    validateParam(
        (String) userRequest.getRequest().get(JsonKey.FIRST_NAME),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.FIRST_NAME);
    if (StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.EMAIL))
        && StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.PHONE))
        && StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.MANAGED_BY))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.emailorPhoneorManagedByRequired);
    }

    if ((StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.EMAIL))
            || StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.PHONE)))
        && StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.MANAGED_BY))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.OnlyEmailorPhoneorManagedByRequired);
    }
    validatePassword((String) userRequest.getRequest().get(JsonKey.PASSWORD));
    if (StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.EMAIL))) {
      validateEmail((String) userRequest.getRequest().get(JsonKey.EMAIL));
    }
    if (StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.PHONE))) {
      validatePhone((String) userRequest.getRequest().get(JsonKey.PHONE));
    }
    if ((null == userRequest.getRequest().get(JsonKey.DOB_VALIDATION_DONE))) {
      validateDob(userRequest);
    }
  }

  public void validateCreateUserV3Request(Request userRequest) {
    validateCreateUserRequest(userRequest);
  }

  public void validateUserCreateV4(Request userRequest) {
    validateUserCreateV3(userRequest);
    validateFrameworkDetails(userRequest);
  }

  public void validateUserLookupRequest(Request request) {
    checkMandatoryFieldsPresent(request.getRequest(), JsonKey.VALUE, JsonKey.KEY);
    List<String> types =
        Stream.of(ProjectUtil.UserLookupType.values())
            .map(ProjectUtil.UserLookupType::getType)
            .collect(Collectors.toList());
    types.add(JsonKey.ID);
    String key = (String) request.get(JsonKey.KEY);
    if (!types.contains(key)) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.invalidValue,
          MessageFormat.format(
              ResponseCode.invalidValue.getErrorMessage(), JsonKey.KEY, key, types));
    }
  }

  public void validateCreateUserV1Request(Request userRequest) {
    validateUserName(userRequest);
    validateCreateUserV3Request(userRequest);
  }

  public void fieldsNotAllowed(List<String> fields, Request userRequest) {
    for (String field : fields) {
      if (((userRequest.getRequest().get(field) instanceof String)
              && StringUtils.isNotBlank((String) userRequest.getRequest().get(field)))
          || (null != userRequest.getRequest().get(field))) {
        throw new ProjectCommonException(
            ResponseCode.invalidRequestParameter.getErrorCode(),
            ProjectUtil.formatMessage(
                ResponseCode.invalidRequestParameter.getErrorMessage(), field),
            ERROR_CODE);
      }
    }
  }

  public void phoneValidation(Request userRequest) {
    if (!StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.COUNTRY_CODE))) {
      boolean bool =
          ProjectUtil.validateCountryCode(
              (String) userRequest.getRequest().get(JsonKey.COUNTRY_CODE));
      if (!bool) {
        ProjectCommonException.throwClientErrorException(ResponseCode.invalidCountryCode);
      }
    }
    if (StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.PHONE))) {
      validatePhoneNo(
          (String) userRequest.getRequest().get(JsonKey.PHONE),
          (String) userRequest.getRequest().get(JsonKey.COUNTRY_CODE));
    }
  }

  /**
   * This method will do basic validation for user request object.
   *
   * @param userRequest
   */
  public void createUserBasicValidation(Request userRequest) {

    createUserBasicProfileFieldsValidation(userRequest);
    if (userRequest.getRequest().containsKey(JsonKey.ROLES)
        && null != userRequest.getRequest().get(JsonKey.ROLES)
        && !(userRequest.getRequest().get(JsonKey.ROLES) instanceof List)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseCode.dataTypeError.getErrorMessage(), JsonKey.ROLES, JsonKey.LIST),
          ERROR_CODE);
    }
  }

  private void createUserBasicProfileFieldsValidation(Request userRequest) {
    validateParam(
        (String) userRequest.getRequest().get(JsonKey.FIRST_NAME),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.FIRST_NAME);
    if (StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.EMAIL))
        && StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.PHONE))
        && StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.MANAGED_BY))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.emailorPhoneorManagedByRequired);
    }

    if ((StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.EMAIL))
            || StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.PHONE)))
        && StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.MANAGED_BY))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.OnlyEmailorPhoneorManagedByRequired);
    }

    if ((null == userRequest.getRequest().get(JsonKey.DOB_VALIDATION_DONE))) {
      validateDob(userRequest);
    }

    if (!StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.EMAIL))
        && !ProjectUtil.isEmailvalid((String) userRequest.getRequest().get(JsonKey.EMAIL))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.emailFormatError);
    }
  }

  public void validateDob(Request userRequest) {
    if (null != userRequest.getRequest().get(JsonKey.DOB)) {
      String dobValue =
          userRequest.getRequest().get(JsonKey.DOB)
              + ProjectUtil.getConfigValue(JsonKey.DEFAULT_MONTH_DATE);
      boolean bool = ProjectUtil.isDateValidFormat(ProjectUtil.YEAR_MONTH_DATE_FORMAT, dobValue);
      if (!bool) {
        ProjectCommonException.throwClientErrorException(ResponseCode.dateFormatError);
      } else {
        userRequest.getRequest().put(JsonKey.DOB, dobValue);
        userRequest.getRequest().put(JsonKey.DOB_VALIDATION_DONE, true);
      }
    }
  }

  private boolean validatePhoneNo(String phone, String countryCode) {
    if (phone.contains("+")) {
      ProjectCommonException.throwClientErrorException(ResponseCode.invalidPhoneNumber);
    }
    if (ProjectUtil.validatePhone(phone, countryCode)) {
      return true;
    } else {
      ProjectCommonException.throwClientErrorException(ResponseCode.phoneNoFormatError);
    }
    return false;
  }

  /**
   * This method will validate update user data.
   *
   * @param userRequest Request
   */
  public void validateUpdateUserRequest(Request userRequest) {
    if (userRequest.getRequest().containsKey(JsonKey.MANAGED_BY)) {
      ProjectCommonException.throwClientErrorException(ResponseCode.managedByNotAllowed);
    }
    checkEmptyPhoneAndEmail(userRequest);
    externalIdsValidation(userRequest, JsonKey.UPDATE);
    phoneValidation(userRequest);
    updateUserBasicValidation(userRequest);
    validateUserOrgField(userRequest);
    if ((null == userRequest.getRequest().get(JsonKey.DOB_VALIDATION_DONE))) {
      validateDob(userRequest);
    }
    if (userRequest.getRequest().containsKey(JsonKey.ROOT_ORG_ID)
        && StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.ROOT_ORG_ID))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.invalidRootOrganisationId);
    }
    validateExtIdTypeAndProvider(userRequest);
    validateFrameworkDetails(userRequest);
    validateRecoveryEmailOrPhone(userRequest);
  }

  public void validateUpdateUserRequestV3(Request userRequest) {
    validateUpdateUserRequest(userRequest);
  }

  public void checkEmptyPhoneAndEmail(Request userRequest) {
    String phone = (String) userRequest.getRequest().get(JsonKey.PHONE);
    String email = (String) userRequest.getRequest().get(JsonKey.EMAIL);
    if (null != phone && phone.equalsIgnoreCase("")) {
      throwInvalidParamValue(JsonKey.PHONE, "");
    }
    if (null != email && email.equalsIgnoreCase("")) {
      throwInvalidParamValue(JsonKey.EMAIL, "");
    }
  }

  private static void throwInvalidParamValue(String param, String value) {
    ProjectCommonException.throwClientErrorException(
        ResponseCode.invalidParameterValue,
        MessageFormat.format(ResponseCode.invalidParameterValue.getErrorMessage(), value, param));
  }

  private void validateUserOrgField(Request userRequest) {
    Map<String, Object> request = userRequest.getRequest();
    if (request.containsKey(JsonKey.ORGANISATIONS)) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.errorUnsupportedField,
          ProjectUtil.formatMessage(
              ResponseCode.errorUnsupportedField.getErrorMessage(), JsonKey.ORGANISATIONS));
    }
  }

  public void externalIdsValidation(Request userRequest, String operation) {
    if (userRequest.getRequest().containsKey(JsonKey.EXTERNAL_IDS)
        && (null != userRequest.getRequest().get(JsonKey.EXTERNAL_IDS))) {
      if (!(userRequest.getRequest().get(JsonKey.EXTERNAL_IDS) instanceof List)) {
        throw new ProjectCommonException(
            ResponseCode.dataTypeError.getErrorCode(),
            ProjectUtil.formatMessage(
                ResponseCode.dataTypeError.getErrorMessage(), JsonKey.EXTERNAL_IDS, JsonKey.LIST),
            ERROR_CODE);
      }
      List<Map<String, String>> externalIds =
          (List<Map<String, String>>) userRequest.getRequest().get(JsonKey.EXTERNAL_IDS);
      validateIndividualExternalId(operation, externalIds);
      if (operation.equalsIgnoreCase(JsonKey.CREATE)) {
        checkForDuplicateExternalId(externalIds);
      }
    }
  }

  private void validateIndividualExternalId(
      String operation, List<Map<String, String>> externalIds) {
    // valid operation type for externalIds in user api.
    List<String> operationTypeList = Arrays.asList(JsonKey.ADD, JsonKey.REMOVE, JsonKey.EDIT);
    externalIds
        .stream()
        .forEach(
            identity -> {
              // check for invalid operation type
              if (StringUtils.isNotBlank(identity.get(JsonKey.OPERATION))
                  && (!operationTypeList.contains(
                      (identity.get(JsonKey.OPERATION)).toLowerCase()))) {
                throw new ProjectCommonException(
                    ResponseCode.invalidValue.getErrorCode(),
                    ProjectUtil.formatMessage(
                        ResponseCode.invalidValue.getErrorMessage(),
                        StringFormatter.joinByDot(JsonKey.EXTERNAL_IDS, JsonKey.OPERATION),
                        identity.get(JsonKey.OPERATION),
                        String.join(StringFormatter.COMMA, operationTypeList)),
                    ERROR_CODE);
              }
              // throw exception for invalid operation if other operation type is coming in
              // request
              // other than add or null for create user api
              if (JsonKey.CREATE.equalsIgnoreCase(operation)
                  && StringUtils.isNotBlank(identity.get(JsonKey.OPERATION))
                  && (!JsonKey.ADD.equalsIgnoreCase(((identity.get(JsonKey.OPERATION)))))) {
                throw new ProjectCommonException(
                    ResponseCode.invalidValue.getErrorCode(),
                    ProjectUtil.formatMessage(
                        ResponseCode.invalidValue.getErrorMessage(),
                        StringFormatter.joinByDot(JsonKey.EXTERNAL_IDS, JsonKey.OPERATION),
                        identity.get(JsonKey.OPERATION),
                        JsonKey.ADD),
                    ERROR_CODE);
              }
              validateExternalIdMandatoryParam(JsonKey.ID, identity.get(JsonKey.ID));
              validateExternalIdMandatoryParam(JsonKey.PROVIDER, identity.get(JsonKey.PROVIDER));
              validateExternalIdMandatoryParam(JsonKey.ID_TYPE, identity.get(JsonKey.ID_TYPE));
            });
  }

  private void validateExternalIdMandatoryParam(String param, String paramValue) {
    if (StringUtils.isBlank(paramValue)) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(),
              StringFormatter.joinByDot(JsonKey.EXTERNAL_IDS, param)),
          ERROR_CODE);
    }
  }

  @SuppressWarnings("rawtypes")
  private void updateUserBasicValidation(Request userRequest) {
    fieldsNotAllowed(
        Arrays.asList(
            JsonKey.REGISTERED_ORG_ID,
            JsonKey.ROOT_ORG_ID,
            JsonKey.CHANNEL,
            JsonKey.USERNAME,
            JsonKey.PROVIDER,
            JsonKey.ID_TYPE),
        userRequest);
    validateUserIdOrExternalId(userRequest);
    if (userRequest.getRequest().containsKey(JsonKey.FIRST_NAME)
        && (StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.FIRST_NAME)))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.firstNameRequired);
    }

    if ((userRequest.getRequest().containsKey(JsonKey.EMAIL)
            && userRequest.getRequest().get(JsonKey.EMAIL) != null)
        && !ProjectUtil.isEmailvalid((String) userRequest.getRequest().get(JsonKey.EMAIL))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.emailFormatError);
    }

    if (userRequest.getRequest().containsKey(JsonKey.ROLES)
        && null != userRequest.getRequest().get(JsonKey.ROLES)) {
      if (userRequest.getRequest().get(JsonKey.ROLES) instanceof List
          && ((List) userRequest.getRequest().get(JsonKey.ROLES)).isEmpty()) {
        ProjectCommonException.throwClientErrorException(ResponseCode.rolesRequired);
      } else if (!(userRequest.getRequest().get(JsonKey.ROLES) instanceof List)) {
        throw new ProjectCommonException(
            ResponseCode.dataTypeError.getErrorCode(),
            ProjectUtil.formatMessage(
                ResponseCode.dataTypeError.getErrorMessage(), JsonKey.ROLES, JsonKey.LIST),
            ERROR_CODE);
      }
    }
    if (userRequest.getOperation().equalsIgnoreCase(ActorOperations.UPDATE_USER.getValue())
        || userRequest.getOperation().equalsIgnoreCase(ActorOperations.UPDATE_USER_V2.getValue())) {
      if (userRequest.getRequest().containsKey(JsonKey.PROFILE_USERTYPES)) {
        ProjectCommonException.throwClientErrorException(
            ResponseCode.invalidParameter, JsonKey.PROFILE_USERTYPES);
      }
    } else {
      if (userRequest.getRequest().containsKey(JsonKey.PROFILE_USERTYPE)) {
        ProjectCommonException.throwClientErrorException(
            ResponseCode.invalidParameter, JsonKey.PROFILE_USERTYPE);
      }
    }
    if (userRequest.getRequest().containsKey(JsonKey.PROFILE_USERTYPES)
        && null != userRequest.getRequest().get(JsonKey.PROFILE_USERTYPES)) {
      if (userRequest.getRequest().get(JsonKey.PROFILE_USERTYPES) instanceof List) {
        List profileusertypes = (List) userRequest.getRequest().get(JsonKey.PROFILE_USERTYPES);
        if (CollectionUtils.isEmpty(profileusertypes)) {
          ProjectCommonException.throwClientErrorException(ResponseCode.profileUserTypesRequired);
        } else {
          try {
            List<Map<String, String>> profUserTypeList =
                (List<Map<String, String>>) userRequest.getRequest().get(JsonKey.PROFILE_USERTYPES);
          } catch (ClassCastException e) {
            throw new ProjectCommonException(
                ResponseCode.dataTypeError.getErrorCode(),
                ProjectUtil.formatMessage(
                    ResponseCode.dataTypeError.getErrorMessage(),
                    JsonKey.PROFILE_USERTYPES,
                    JsonKey.LIST),
                ERROR_CODE);
          }
        }
      } else if (!(userRequest.getRequest().get(JsonKey.PROFILE_USERTYPES) instanceof List)) {
        throw new ProjectCommonException(
            ResponseCode.dataTypeError.getErrorCode(),
            ProjectUtil.formatMessage(
                ResponseCode.dataTypeError.getErrorMessage(),
                JsonKey.PROFILE_USERTYPES,
                JsonKey.LIST),
            ERROR_CODE);
      }
    }
  }

  private void validateUserIdOrExternalId(Request userRequest) {
    if ((StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.USER_ID))
            && StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.ID)))
        && (StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID))
            || StringUtils.isBlank(
                (String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID_PROVIDER))
            || StringUtils.isBlank(
                (String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID_TYPE)))) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(),
              (StringFormatter.joinByOr(
                  JsonKey.USER_ID,
                  StringFormatter.joinByAnd(
                      StringFormatter.joinByComma(JsonKey.EXTERNAL_ID, JsonKey.EXTERNAL_ID_TYPE),
                      JsonKey.EXTERNAL_ID_PROVIDER)))),
          ERROR_CODE);
    }
  }

  /**
   * This method will validate verifyUser requested data.
   *
   * @param userRequest Request
   */
  public void validateVerifyUser(Request userRequest) {
    if (StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.LOGIN_ID))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.loginIdRequired);
    }
  }

  /**
   * Either user will send UserId or (provider and externalId).
   *
   * @param request
   */
  public void validateAssignRole(Request request) {
    if (StringUtils.isBlank((String) request.getRequest().get(JsonKey.USER_ID))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.userIdRequired);
    }

    if (request.getRequest().get(JsonKey.ROLES) == null
        || !(request.getRequest().get(JsonKey.ROLES) instanceof List)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseCode.dataTypeError.getErrorMessage(), JsonKey.ROLES, JsonKey.LIST),
          ERROR_CODE);
    }

    String organisationId = (String) request.getRequest().get(JsonKey.ORGANISATION_ID);
    String externalId = (String) request.getRequest().get(JsonKey.EXTERNAL_ID);
    String provider = (String) request.getRequest().get(JsonKey.PROVIDER);
    if (StringUtils.isBlank(organisationId)
        && (StringUtils.isBlank(externalId) || StringUtils.isBlank(provider))) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(),
              (StringFormatter.joinByOr(
                  JsonKey.ORGANISATION_ID,
                  StringFormatter.joinByAnd(JsonKey.EXTERNAL_ID, JsonKey.PROVIDER)))),
          ERROR_CODE);
    }
  }

  /** @param request */
  public void validateForgotPassword(Request request) {
    if (request.getRequest().get(JsonKey.USERNAME) == null
        || StringUtils.isBlank((String) request.getRequest().get(JsonKey.USERNAME))) {
      throw new ProjectCommonException(
          ResponseCode.userNameRequired.getErrorCode(),
          ResponseCode.userNameRequired.getErrorMessage(),
          ERROR_CODE);
    }
  }

  private void validateExtIdTypeAndProvider(Request userRequest) {
    if ((StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID_PROVIDER))
        && StringUtils.isNotBlank((String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID))
        && StringUtils.isNotBlank(
            (String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID_TYPE)))) {
      return;
    } else if (StringUtils.isBlank(
            (String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID_PROVIDER))
        && StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID))
        && StringUtils.isBlank((String) userRequest.getRequest().get(JsonKey.EXTERNAL_ID_TYPE))) {
      return;
    } else {
      throw new ProjectCommonException(
          ResponseCode.dependentParamsMissing.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseCode.dependentParamsMissing.getErrorMessage(),
              StringFormatter.joinByComma(
                  JsonKey.EXTERNAL_ID, JsonKey.EXTERNAL_ID_TYPE, JsonKey.EXTERNAL_ID_PROVIDER)),
          ERROR_CODE);
    }
  }

  private void checkForDuplicateExternalId(List<Map<String, String>> list) {
    List<Map<String, String>> checkedList = new ArrayList<>();
    for (Map<String, String> externalId : list) {
      for (Map<String, String> checkedExternalId : checkedList) {
        String provider = checkedExternalId.get(JsonKey.PROVIDER);
        String idType = checkedExternalId.get(JsonKey.ID_TYPE);
        if (provider.equalsIgnoreCase(externalId.get(JsonKey.PROVIDER))
            && idType.equalsIgnoreCase(externalId.get(JsonKey.ID_TYPE))) {
          String exceptionMsg =
              MessageFormat.format(
                  ResponseCode.duplicateExternalIds.getErrorMessage(), idType, provider);
          ProjectCommonException.throwClientErrorException(
              ResponseCode.duplicateExternalIds, exceptionMsg);
        }
      }
      checkedList.add(externalId);
    }
  }

  @SuppressWarnings("unchecked")
  private void validateFrameworkDetails(Request request) {
    if (request.getRequest().containsKey(JsonKey.FRAMEWORK)
        && (!(request.getRequest().get(JsonKey.FRAMEWORK) instanceof Map))) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          ResponseCode.dataTypeError.getErrorMessage(),
          ERROR_CODE,
          JsonKey.FRAMEWORK,
          JsonKey.MAP);
    } else {
      Map<String, Object> framework =
          (Map<String, Object>) request.getRequest().get(JsonKey.FRAMEWORK);
      if (!MapUtils.isEmpty(framework)) {
        if (null != framework.get(JsonKey.ID) && (framework.get(JsonKey.ID) instanceof List)) {
          List<String> frameworkId = (List<String>) framework.get(JsonKey.ID);
          if (CollectionUtils.isEmpty(frameworkId)) {
            ProjectCommonException.throwClientErrorException(
                ResponseCode.mandatoryParamsMissing,
                MessageFormat.format(
                    ResponseCode.mandatoryParamsMissing.getErrorMessage(),
                    StringFormatter.joinByDot(JsonKey.FRAMEWORK, JsonKey.ID)));
          } else if (frameworkId.size() > 1) {
            throw new ProjectCommonException(
                ResponseCode.errorInvalidParameterSize.getErrorCode(),
                ResponseCode.errorInvalidParameterSize.getErrorMessage(),
                ERROR_CODE,
                StringFormatter.joinByDot(JsonKey.FRAMEWORK, JsonKey.ID),
                "1",
                String.valueOf(frameworkId.size()));
          } else {
            if (frameworkId.size() == 1) {
              String id = frameworkId.get(0);
              if (StringUtils.isBlank(id)) {
                ProjectCommonException.throwClientErrorException(
                    ResponseCode.mandatoryParamsMissing,
                    MessageFormat.format(
                        ResponseCode.mandatoryParamsMissing.getErrorMessage(),
                        StringFormatter.joinByDot(JsonKey.FRAMEWORK, JsonKey.ID)));
              }
            }
          }
        } else if (null != framework.get(JsonKey.ID)
            && framework.get(JsonKey.ID) instanceof String) {
          String frameworkId = (String) framework.get(JsonKey.ID);
          if (StringUtils.isBlank(frameworkId)) {
            ProjectCommonException.throwClientErrorException(
                ResponseCode.mandatoryParamsMissing,
                MessageFormat.format(
                    ResponseCode.mandatoryParamsMissing.getErrorMessage(),
                    StringFormatter.joinByDot(JsonKey.FRAMEWORK, JsonKey.ID)));
          }
        } else {
          ProjectCommonException.throwClientErrorException(
              ResponseCode.mandatoryParamsMissing,
              MessageFormat.format(
                  ResponseCode.mandatoryParamsMissing.getErrorMessage(),
                  StringFormatter.joinByDot(JsonKey.FRAMEWORK, JsonKey.ID)));
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void validateMandatoryFrameworkFields(
      Map<String, Object> userMap,
      List<String> frameworkFields,
      List<String> frameworkMandatoryFields) {
    if (userMap.containsKey(JsonKey.FRAMEWORK)) {
      Map<String, Object> frameworkRequest = (Map<String, Object>) userMap.get(JsonKey.FRAMEWORK);
      for (String field : frameworkFields) {
        if (CollectionUtils.isNotEmpty(frameworkMandatoryFields)
            && frameworkMandatoryFields.contains(field)) {
          if (!frameworkRequest.containsKey(field)) {
            validateParam(null, ResponseCode.mandatoryParamsMissing, field);
          }
          validateListParamWithPrefix(frameworkRequest, JsonKey.FRAMEWORK, field);
          List<String> fieldValue = (List) frameworkRequest.get(field);
          if (fieldValue.isEmpty()) {
            throw new ProjectCommonException(
                ResponseCode.errorMandatoryParamsEmpty.getErrorCode(),
                ResponseCode.errorMandatoryParamsEmpty.getErrorMessage(),
                ERROR_CODE,
                StringFormatter.joinByDot(JsonKey.FRAMEWORK, field));
          }
        } else {
          if (frameworkRequest.containsKey(field)
              && frameworkRequest.get(field) != null
              && !(frameworkRequest.get(field) instanceof List)) {
            throw new ProjectCommonException(
                ResponseCode.dataTypeError.getErrorCode(),
                ResponseCode.dataTypeError.getErrorMessage(),
                ERROR_CODE,
                field,
                JsonKey.LIST);
          }
        }
      }
      List<String> frameworkRequestFieldList =
          frameworkRequest.keySet().stream().collect(Collectors.toList());
      for (String frameworkRequestField : frameworkRequestFieldList) {
        if (!frameworkFields.contains(frameworkRequestField)) {
          throw new ProjectCommonException(
              ResponseCode.errorUnsupportedField.getErrorCode(),
              ResponseCode.errorUnsupportedField.getErrorMessage(),
              ERROR_CODE,
              StringFormatter.joinByDot(JsonKey.FRAMEWORK, frameworkRequestField));
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void validateFrameworkCategoryValues(
      Map<String, Object> userMap, Map<String, List<Map<String, String>>> frameworkMap) {
    Map<String, List<String>> fwRequest =
        (Map<String, List<String>>) userMap.get(JsonKey.FRAMEWORK);
    for (Map.Entry<String, List<String>> fwRequestFieldEntry : fwRequest.entrySet()) {
      if (!fwRequestFieldEntry.getValue().isEmpty()) {
        List<String> allowedFieldValues =
            getKeyValueFromFrameWork(fwRequestFieldEntry.getKey(), frameworkMap)
                .stream()
                .map(fieldMap -> fieldMap.get(JsonKey.NAME))
                .collect(Collectors.toList());

        List<String> fwRequestFieldList = fwRequestFieldEntry.getValue();

        for (String fwRequestField : fwRequestFieldList) {
          if (!allowedFieldValues.contains(fwRequestField)) {
            throw new ProjectCommonException(
                ResponseCode.invalidParameterValue.getErrorCode(),
                ResponseCode.invalidParameterValue.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode(),
                fwRequestField,
                StringFormatter.joinByDot(JsonKey.FRAMEWORK, fwRequestFieldEntry.getKey()));
          }
        }
      }
    }
  }

  private List<Map<String, String>> getKeyValueFromFrameWork(
      String key, Map<String, List<Map<String, String>>> frameworkMap) {
    if (frameworkMap.get(key) == null) {
      throw new ProjectCommonException(
          ResponseCode.errorUnsupportedField.getErrorCode(),
          MessageFormat.format(
              ResponseCode.errorUnsupportedField.getErrorMessage(),
              key + " in " + JsonKey.FRAMEWORK),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    return frameworkMap.get(key);
  }

  // TODO:  Validate userType with data from form api
  public String validateUserType(
      Map<String, Object> userRequestMap, String stateCode, RequestContext context) {
    String userType = (String) userRequestMap.get(JsonKey.USER_TYPE);
    if (null != userType) {
      Map<String, Map<String, List<String>>> userTypeConfigMap =
          DataCacheHandler.getUserTypesConfig();
      if (StringUtils.isBlank(stateCode)) {
        stateCode = JsonKey.DEFAULT_PERSONA;
      }

      if (!userTypeConfigMap.containsKey(stateCode)) {
        // Get profile data config
        Map<String, List<String>> userProfileConfigMap =
            FormApiUtil.getUserTypeConfig(FormApiUtil.getProfileConfig(stateCode, context));
        if (MapUtils.isEmpty(userProfileConfigMap)) {
          // Get Default Config
          stateCode = JsonKey.DEFAULT_PERSONA;
          userProfileConfigMap = userTypeConfigMap.get(stateCode);
          if (MapUtils.isEmpty(userProfileConfigMap)) {
            userProfileConfigMap =
                FormApiUtil.getUserTypeConfig(FormApiUtil.getProfileConfig(stateCode, context));
            if (MapUtils.isNotEmpty(userProfileConfigMap)) {
              userTypeConfigMap.put(stateCode, userProfileConfigMap);
            } else {
              logger.info(
                  context, String.format("Form Config not found for stateCode:%s", stateCode));
            }
          }
        } else {
          userTypeConfigMap.put(stateCode, userProfileConfigMap);
        }
      }

      Map<String, List<String>> userTypeMap = userTypeConfigMap.get(stateCode);
      if (MapUtils.isEmpty(userTypeMap)) {
        ProjectCommonException.throwClientErrorException(
            ResponseCode.SERVER_ERROR,
            MessageFormat.format(ResponseMessage.Message.USER_TYPE_CONFIG_IS_EMPTY, stateCode));
      }
      logger.info(
          context,
          String.format(
              "Available User Type for stateCode:%s are %s", stateCode, userTypeMap.keySet()));
      List<Map> profileUserTypes = (List<Map>) userRequestMap.get(JsonKey.PROFILE_USERTYPES);
      if (CollectionUtils.isNotEmpty(profileUserTypes)
          && MapUtils.isNotEmpty((Map) profileUserTypes.get(0))) {
        profileUserTypes.forEach(
            item -> {
              String userTypeItem = (String) item.get(JsonKey.TYPE);
              if (!userTypeMap.containsKey(userTypeItem)) {
                ProjectCommonException.throwClientErrorException(
                    ResponseCode.invalidParameterValue,
                    MessageFormat.format(
                        ResponseCode.invalidParameterValue.getErrorMessage(),
                        new String[] {userType, JsonKey.USER_TYPE}));
              }
            });
      } else if (!userTypeMap.containsKey(userType)) {
        ProjectCommonException.throwClientErrorException(
            ResponseCode.invalidParameterValue,
            MessageFormat.format(
                ResponseCode.invalidParameterValue.getErrorMessage(),
                new String[] {userType, JsonKey.USER_TYPE}));
      }
    }
    return stateCode;
  }

  public void validateUserSubType(Map<String, Object> userRequestMap, String stateCode) {
    String userType = (String) userRequestMap.get(JsonKey.USER_TYPE);
    String userSubType = (String) userRequestMap.get(JsonKey.USER_SUB_TYPE);
    Map<String, Map<String, List<String>>> userTypeConfigMap =
        DataCacheHandler.getUserTypesConfig();
    Map<String, List<String>> userTypeMap = userTypeConfigMap.get(stateCode);

    List<Map> profileUserTypes = (List<Map>) userRequestMap.get(JsonKey.PROFILE_USERTYPES);
    if (CollectionUtils.isNotEmpty(profileUserTypes)
        && MapUtils.isNotEmpty(profileUserTypes.get(0))) {
      profileUserTypes.forEach(
          item -> {
            String userTypeItem = (String) item.get(JsonKey.TYPE);
            String userSubTypeItem = (String) item.get(JsonKey.SUB_TYPE);
            if (StringUtils.isNotEmpty(userSubTypeItem)) {
              if (null == userTypeMap.get(userTypeItem)
                  || !userTypeMap.get(userTypeItem).contains(userSubTypeItem)) {
                ProjectCommonException.throwClientErrorException(
                    ResponseCode.invalidParameterValue,
                    MessageFormat.format(
                        ResponseCode.invalidParameterValue.getErrorMessage(),
                        new String[] {userSubTypeItem, JsonKey.USER_SUB_TYPE}));
              }
            }
          });
    } else if (null != userSubType
        && (null == userTypeMap.get(userType)
            || !userTypeMap.get(userType).contains(userSubType))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.invalidParameterValue,
          MessageFormat.format(
              ResponseCode.invalidParameterValue.getErrorMessage(),
              new String[] {userSubType, JsonKey.USER_SUB_TYPE}));
    }
  }

  public void validateUserMergeRequest(
      Request request, String authUserToken, String sourceUserToken) {
    if (StringUtils.isBlank((String) request.getRequest().get(JsonKey.FROM_ACCOUNT_ID))) {
      throw new ProjectCommonException(
          ResponseCode.fromAccountIdRequired.getErrorCode(),
          ResponseCode.fromAccountIdRequired.getErrorMessage(),
          ERROR_CODE);
    }

    if (StringUtils.isBlank((String) request.getRequest().get(JsonKey.TO_ACCOUNT_ID))) {
      throw new ProjectCommonException(
          ResponseCode.toAccountIdRequired.getErrorCode(),
          ResponseCode.toAccountIdRequired.getErrorMessage(),
          ERROR_CODE);
    }

    if (StringUtils.isBlank(authUserToken)) {
      createClientError(
          ResponseCode.mandatoryHeaderParamsMissing, JsonKey.X_AUTHENTICATED_USER_TOKEN);
    }

    if (StringUtils.isBlank(authUserToken)) {
      createClientError(
          ResponseCode.mandatoryHeaderParamsMissing, JsonKey.X_AUTHENTICATED_USER_TOKEN);
    }
    if (StringUtils.isBlank(sourceUserToken)) {
      createClientError(ResponseCode.mandatoryHeaderParamsMissing, JsonKey.X_SOURCE_USER_TOKEN);
    }
  }

  private void validateRecoveryEmailOrPhone(Request userRequest) {
    if (StringUtils.isNotBlank((String) userRequest.get(JsonKey.RECOVERY_EMAIL))) {
      validateEmail((String) userRequest.get(JsonKey.RECOVERY_EMAIL));
    }
    if (StringUtils.isNotBlank((String) userRequest.get(JsonKey.RECOVERY_PHONE))) {
      validatePhone((String) userRequest.get(JsonKey.RECOVERY_PHONE));
    }
  }

  /**
   * This method will validate uuid.
   *
   * @param uuid String
   */
  public void validateUserId(String uuid) {
    if (StringUtils.isNotEmpty(uuid) && !ProjectUtil.validateUUID(uuid)) {
      ProjectCommonException.throwClientErrorException(ResponseCode.invalidRequestParameter);
    }
  }

  public void validateUserDeclarationRequest(Request userDeclareRequest) {
    try {
      List<Map<String, Object>> declarations =
          (List<Map<String, Object>>) userDeclareRequest.getRequest().get(JsonKey.DECLARATIONS);
      if (CollectionUtils.isEmpty(declarations)) {
        throw new ProjectCommonException(
            ResponseCode.mandatoryParamsMissing.getErrorCode(),
            MessageFormat.format(
                ResponseCode.mandatoryParamsMissing.getErrorMessage(), JsonKey.DECLARATIONS),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      } else {
        for (Map<String, Object> declareFields : declarations) {
          String userId = (String) declareFields.get(JsonKey.USER_ID);
          String orgId = (String) declareFields.get(JsonKey.ORG_ID);
          if (StringUtils.isBlank(userId) || StringUtils.isBlank(orgId)) {
            throw new ProjectCommonException(
                ResponseCode.mandatoryParamsMissing.getErrorCode(),
                MessageFormat.format(
                    ResponseMessage.Message.MISSING_SELF_DECLARED_MANDATORY_PARAMETERS,
                    new String[] {JsonKey.USER_ID, JsonKey.ORG_ID}),
                ResponseCode.CLIENT_ERROR.getResponseCode());
          }
          if (StringUtils.isBlank((String) declareFields.get(JsonKey.PERSONA))) {
            declareFields.put(JsonKey.PERSONA, JsonKey.DEFAULT_PERSONA);
          }
        }
      }
    } catch (Exception ex) {
      throw new ProjectCommonException(
          ResponseCode.invalidParameterValue.getErrorCode(),
          ex.getMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }
}
