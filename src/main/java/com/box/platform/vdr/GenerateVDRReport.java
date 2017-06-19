package com.box.platform.vdr;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.box.platform.vdr.entity.BoxExcelItem;
import com.box.sdk.*;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * Created by kadams on 3/29/17.
 */
public class GenerateVDRReport implements RequestHandler<Object, Object> {
    private LambdaLogger logger;
    private BoxDeveloperEditionAPIConnection api = null;
    private String boxEnterpriseId = null;
    private String boxClientId = null;
    private String boxClientSecret = null;
    private String boxPrivateKeyPath = null;
    private String boxPublicKeyId = null;
    private String boxKeyPassphrase = null;
    private String reportFolderName = null;
    private String reportFileName = null;

    private static final int MAX_CACHE_ENTRIES = 100;
    private static final String[] REPORT_COLUMN_HEADERS = {
            "Deal Room",
            "Id",
            "Path",
            "Name",
            "Type",
            "Item Count",
            "Created At",
            "Created By Login",
            "Created By Name",
            "Modified At",
            "Modified By Login",
            "Modified By Name",
            "Original Content Creation Date",
            "Original Content Modified Date"
        };

    private static final String EXCEL_SHEET_NAME = "VDR Index";

    private List<BoxExcelItem> excelDataList = new ArrayList<BoxExcelItem>();
    private String userLogin = null;
    private String dealRoomName = null;
    private String startingFolderId = null;
    private String reportFolderId = null;
    private String reportFileId = null;
    private File tempReportFile = null;

    // Handle the Box request that comes from a right-click action
    public Object handleRequest(Object jsonObject, Context context) {
        logger = context.getLogger();
        JsonObject responseJson = Json.object();
        try{
            // Get Box Connection parameters
            boxEnterpriseId = System.getenv("BOX_ENTERPRISE_ID");
            logger.log("Found enterprise id: "+ boxEnterpriseId);

            boxClientId = System.getenv("BOX_CLIENT_ID");
            logger.log("Found client id: " + boxClientId);

            boxClientSecret = System.getenv("BOX_CLIENT_SECRET");
            logger.log("Found client secret: " + boxClientSecret);

            boxPrivateKeyPath = System.getenv("BOX_PRIVATE_KEY_PATH");
            logger.log("Found private key path: " + boxPrivateKeyPath);

            boxPublicKeyId = System.getenv("BOX_PUBLIC_KEY_ID");
            logger.log("Found public key id: " + boxPublicKeyId);

            boxKeyPassphrase = System.getenv("BOX_KEY_PASSPHRASE");
            logger.log("Found key passphrase: " + boxKeyPassphrase);

            reportFolderName = System.getenv("REPORT_FOLDER_NAME");
            logger.log("Found report folder name: " + reportFolderName);

            reportFileName = System.getenv("REPORT_FILE_NAME");
            logger.log("Found report file name: " + reportFileName);

            // Get the JSON object from the webhook
            JsonObject boxJson = Json.parse(new Gson().toJson(jsonObject)).asObject();

            // Get the user_id and file_id query string parameters
            // When configuring a webapp integration for a folder, the file_id will actually be the folder_id
            userLogin = boxJson.get("queryStringParameters").asObject().get("user_id").asString();
            startingFolderId = boxJson.get("queryStringParameters").asObject().get("file_id").asString();

            // Set the connection using the userLogin
            setBoxConnection(userLogin);

            // Create the parent folder row for the Excel spreadsheet
            createParentFolderRow(startingFolderId);

            // Populate the response back to the Box webapp
            responseJson.add("status", 200).add("response", "Successfully generated report with id: " + reportFileId);

            // Begin walking the folder tree
            walkFolderTree(startingFolderId);

            // Create the Excel spreadsheet
            createExcel();

            // Check if the report folder exists
            boolean reportFolderExists = reportFolderExists(startingFolderId);
            logger.log("Does report folder exist? " + reportFolderExists);

            // If it does not exist, create the report folder
            if(!reportFolderExists){
                createReportFolder(startingFolderId);
            }

            // Check if the report file exists
            boolean reportFileExists = reportFileExists(reportFolderId);

            // If the report file exists, upload the file as a new version. If not, upload a new file
            if(reportFileExists){
                uploadReportVersion(reportFileId);
            }
            else{
                createReportFile(reportFolderId);
            }

            logger.log("Entering handleRequest method with context: " + context);
        }
        catch (Exception e){
            responseJson.add("status", 500).add("response", "Failed to generate report" + e.getMessage());
            e.printStackTrace();
        }



        return responseJson;
    }

    /**
     *  Start walking down the folder tree given a startingFolderId
     * @param startingFolderId
     */
    private void walkFolderTree(String startingFolderId){
        try{
            // Get the starting folder
            BoxFolder startingFolder = new BoxFolder(this.api, startingFolderId);

            // Get the children iterator, then loop through the children
            Iterator<BoxItem.Info> folderChildrenIter = startingFolder.getChildren().iterator();
            while(folderChildrenIter.hasNext()){
                BoxItem.Info itemInfo = folderChildrenIter.next();
                if(!itemInfo.getName().equalsIgnoreCase(reportFolderName)){
                    BoxExcelItem boxExcelItem = new BoxExcelItem();

                    // Check if the child item is a folder or document
                    if(itemInfo instanceof BoxFolder.Info){
                        BoxFolder.Info boxFolderInfo  = getFolderInfo(itemInfo.getID());

                        boxExcelItem.setDealRoom(dealRoomName);
                        boxExcelItem.setId(boxFolderInfo.getID());
                        String itemPath = getPath(boxFolderInfo);
                        boxExcelItem.setPath(itemPath);
                        boxExcelItem.setName(boxFolderInfo.getName());
                        boxExcelItem.setType("folder");
                        boxExcelItem.setItemCount(Iterables.size(boxFolderInfo.getResource().getChildren()));
                        boxExcelItem.setCreatedAt(boxFolderInfo.getCreatedAt());
                        boxExcelItem.setCreatedByLogin(boxFolderInfo.getCreatedBy().getLogin());
                        boxExcelItem.setCreatedByName(boxFolderInfo.getCreatedBy().getName());
                        boxExcelItem.setModifiedAt(boxFolderInfo.getModifiedAt());
                        boxExcelItem.setModifiedByLogin(boxFolderInfo.getModifiedBy().getLogin());
                        boxExcelItem.setModifiedByName(boxFolderInfo.getModifiedBy().getName());
                        excelDataList.add(boxExcelItem);

                        logger.log("Found folder with id: " + boxFolderInfo.getID() + " and path: " + itemPath);
                        // If the child is an instance of a folder and it has children, then recurse through the walkFolderTree method
                        if(boxFolderInfo.getResource().getChildren().iterator().hasNext()){
                            logger.log("Found folder with children. Recurse!");
                            walkFolderTree((boxFolderInfo.getID()));
                        }
                    }
                    else {
                        BoxFile.Info boxFileInfo  = getFileInfo(itemInfo.getID());
                        boxExcelItem.setDealRoom(dealRoomName);
                        boxExcelItem.setId(boxFileInfo.getID());
                        String itemPath = getPath(boxFileInfo);
                        boxExcelItem.setPath(itemPath);
                        boxExcelItem.setName(boxFileInfo.getName());
                        boxExcelItem.setType("file");
                        boxExcelItem.setCreatedAt(boxFileInfo.getCreatedAt());
                        boxExcelItem.setCreatedByLogin(boxFileInfo.getCreatedBy().getLogin());
                        boxExcelItem.setCreatedByName(boxFileInfo.getCreatedBy().getName());
                        boxExcelItem.setModifiedAt(boxFileInfo.getModifiedAt());
                        boxExcelItem.setModifiedByLogin(boxFileInfo.getModifiedBy().getLogin());
                        boxExcelItem.setModifiedByName(boxFileInfo.getModifiedBy().getName());
                        boxExcelItem.setOriginalContentCreationDate(boxFileInfo.getContentCreatedAt());
                        boxExcelItem.setOriginalContentModifiedDate(boxFileInfo.getContentModifiedAt());
                        excelDataList.add(boxExcelItem);
                        logger.log("Found folder with id: " + boxFileInfo.getID() + " and path: " + itemPath);

                    }
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Check if the report folder exists.
     * @param startingFolderId
     * @return
     */
    private boolean reportFolderExists(String startingFolderId){
        boolean doesReportFolderExist = false;
        try{
            // Get the starting folder by id
            BoxFolder startingFolder = new BoxFolder(this.api, startingFolderId);

            // Get the folder children and check if one of the children has the report folder name
            Iterator<BoxItem.Info> childrenIterator = startingFolder.getChildren().iterator();
            while (childrenIterator.hasNext()){
                BoxItem.Info childInfo = childrenIterator.next();

                if(childInfo instanceof BoxFolder.Info){
                    String folderName = childInfo.getName();
                    if(folderName.equalsIgnoreCase(reportFolderName)){
                        doesReportFolderExist = true;
                        reportFolderId = childInfo.getID();
                    }
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return doesReportFolderExist;
    }

    /**
     * Create the folder to contain the report file
     * @param startingFolderId
     */
    private void createReportFolder(String startingFolderId){
        try{
            BoxFolder startingFolder = new BoxFolder(api, startingFolderId);
            BoxFolder.Info reportFolderInfo = startingFolder.createFolder(reportFolderName);
            reportFolderId = reportFolderInfo.getID();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Check if the report file exists
     * @param reportFolderId
     * @return
     */
    private boolean reportFileExists(String reportFolderId){
        boolean doesReportFileExist = false;

        try{
            BoxFolder reportFolder = new BoxFolder(api, reportFolderId);
            Iterator<BoxItem.Info> childrenIterator = reportFolder.getChildren().iterator();
            while (childrenIterator.hasNext()){
                BoxItem.Info childInfo = childrenIterator.next();

                if(childInfo instanceof BoxFile.Info){
                    String reportFileName = childInfo.getName();
                    if(reportFileName.equalsIgnoreCase(reportFileName)){
                        logger.log("Found the report report excel file!!!");
                        doesReportFileExist = true;
                        reportFileId = childInfo.getID();
                    }
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return doesReportFileExist;
    }

    /**
     * Create the first row in the Excel spreadsheet that represents the parent folder row
     * @param startingFolderId
     */
    private void createParentFolderRow(String startingFolderId){
        try{
            BoxFolder.Info startingFolderInfo = getFolderInfo(startingFolderId);

            BoxExcelItem startingFolder = new BoxExcelItem();
            dealRoomName = startingFolderInfo.getName();
            startingFolder.setDealRoom(dealRoomName);
            startingFolder.setId(startingFolderInfo.getID());
            startingFolder.setPath(getPath(startingFolderInfo));
            startingFolder.setName(startingFolderInfo.getName());
            startingFolder.setType("folder");
            startingFolder.setItemCount(getItemCount(startingFolderInfo.getID()));
            startingFolder.setCreatedAt(startingFolderInfo.getCreatedAt());
            startingFolder.setCreatedByLogin(startingFolderInfo.getCreatedBy().getLogin());
            startingFolder.setCreatedByName(startingFolderInfo.getCreatedBy().getName());
            startingFolder.setModifiedAt(startingFolderInfo.getModifiedAt());
            startingFolder.setModifiedByLogin(startingFolderInfo.getModifiedBy().getLogin());
            startingFolder.setModifiedByName(startingFolderInfo.getModifiedBy().getName());

            excelDataList.add(startingFolder);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Crete the Excel spreadsheet using Apache POI
     */
    private void createExcel(){
        try {
            // Create a temp file and corresponding outputstream for the XSSFWorkbook
            tempReportFile = File.createTempFile(reportFileName, ".tmp");
            FileOutputStream excelOutputStream = new FileOutputStream(tempReportFile);
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet worksheet = workbook.createSheet(EXCEL_SHEET_NAME);

            // Set the Cell header style
            CellStyle headerCellStyle = worksheet.getWorkbook().createCellStyle();
            Font font = worksheet.getWorkbook().createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 12);
            headerCellStyle.setFont(font);
            Row headerRow = worksheet.createRow(0);
            for (int i = 0; i < REPORT_COLUMN_HEADERS.length; i++) {
                Cell headerCell = headerRow.createCell(i);
                headerCell.setCellStyle(headerCellStyle);
                headerCell.setCellValue(REPORT_COLUMN_HEADERS[i]);
            }

            // Set the data cell style
            CreationHelper createHelper = workbook.getCreationHelper();
            CellStyle dateCellStyle = workbook.createCellStyle();
            dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("mm/dd/yyyy hh:mm:ss"));

            // Create the new row and corresponding cells
            int rowCount = 1;
            for(BoxExcelItem boxExcelItem : excelDataList){
                Row row = worksheet.createRow(rowCount);

                Cell dealRoomCell = row.createCell(0);
                dealRoomCell.setCellValue(boxExcelItem.getDealRoom());

                Cell idCell = row.createCell(1);
                idCell.setCellValue(boxExcelItem.getId());

                Cell pathCell = row.createCell(2);
                pathCell.setCellValue(boxExcelItem.getPath());

                Cell nameCell = row.createCell(3);
                nameCell.setCellValue(boxExcelItem.getName());

                Cell typeCell = row.createCell(4);
                typeCell.setCellValue(boxExcelItem.getType());

                Cell itemCountCell = row.createCell(5);
                itemCountCell.setCellValue(boxExcelItem.getItemCount());

                Cell createAtCell = row.createCell(6);
                createAtCell.setCellStyle(dateCellStyle);
                createAtCell.setCellValue(boxExcelItem.getCreatedAt());

                Cell createdByLoginCell = row.createCell(7);
                createdByLoginCell.setCellValue(boxExcelItem.getCreatedByLogin());

                Cell createdByNameCell = row.createCell(8);
                createdByNameCell.setCellValue(boxExcelItem.getCreatedByName());

                Cell modifiedAtCell = row.createCell(9);
                modifiedAtCell.setCellStyle(dateCellStyle);
                modifiedAtCell.setCellValue(boxExcelItem.getModifiedAt());

                Cell modifiedByLoginCell = row.createCell(10);
                modifiedByLoginCell.setCellValue(boxExcelItem.getModifiedByLogin());

                Cell modifiedByLoginNameCell = row.createCell(11);
                modifiedByLoginNameCell.setCellValue(boxExcelItem.getModifiedByName());

                Cell originalContentCreationDateCell = row.createCell(12);
                originalContentCreationDateCell.setCellStyle(dateCellStyle);
                originalContentCreationDateCell.setCellValue(boxExcelItem.getOriginalContentCreationDate());

                Cell originalContentModifiedDateCell = row.createCell(13);
                originalContentModifiedDateCell.setCellStyle(dateCellStyle);
                originalContentModifiedDateCell.setCellValue(boxExcelItem.getOriginalContentModifiedDate());

                rowCount++;
            }

            // Auto size the columns. Note that for some reason, you need to autosize each and every column and you can do the whole sheet
            worksheet.autoSizeColumn(0);
            worksheet.autoSizeColumn(1);
            worksheet.autoSizeColumn(2);
            worksheet.autoSizeColumn(3);
            worksheet.autoSizeColumn(4);
            worksheet.autoSizeColumn(5);
            worksheet.autoSizeColumn(6);
            worksheet.autoSizeColumn(7);
            worksheet.autoSizeColumn(8);
            worksheet.autoSizeColumn(9);
            worksheet.autoSizeColumn(10);
            worksheet.autoSizeColumn(11);
            worksheet.autoSizeColumn(12);
            worksheet.autoSizeColumn(13);

            // Write the workbook and close the stream.
            workbook.write(excelOutputStream);
            excelOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create the report file
     * @param reportFolderId
     */
    private void createReportFile(String reportFolderId){
        try{
            FileInputStream reportFileStream = new FileInputStream(tempReportFile);
            BoxFolder reportFolder = new BoxFolder(api, reportFolderId);
            reportFolder.uploadFile(reportFileStream, reportFileName);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Upload the report file as a new version
     * @param reportFileId
     */
    private void uploadReportVersion(String reportFileId){
        try{
            FileInputStream reportFileStream = new FileInputStream(tempReportFile);
            BoxFile reportFile = new BoxFile(api, reportFileId);
            reportFile.uploadVersion(reportFileStream);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }


    /**
     * Get the path of a particular item
     * @param boxItemInfo
     * @return
     */
    private String getPath(BoxItem.Info boxItemInfo){
        String itemPath = "";
        try{
            for(BoxItem.Info pathItemInfo: boxItemInfo.getPathCollection()){
                itemPath += "/" + pathItemInfo.getName();
            }
            itemPath += "/" + boxItemInfo.getName();
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return itemPath;
    }

    /**
     * Get the folder item given a folderId
     * @param folderId
     * @return
     */
    private BoxFolder.Info getFolderInfo(String folderId){
        BoxFolder.Info folderInfo = null;
        try{
            BoxFolder folder = new BoxFolder(this.api, folderId);
            folderInfo = folder.getInfo();
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return folderInfo;
    }

    /**
     * Get the file info given a fileId
     * @param fileId
     * @return
     */
    private BoxFile.Info getFileInfo(String fileId){
        BoxFile.Info fileInfo = null;
        try{
            BoxFile file = new BoxFile(this.api, fileId);
            fileInfo = file.getInfo();
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return fileInfo;
    }

    /**
     * Get the item count for a folder
     * @param folderId
     * @return
     */
    private int getItemCount(String folderId){
        int itemCount = -1;
        try{
            BoxFolder folder = new BoxFolder(this.api, folderId);
            itemCount = Iterables.size(folder.getChildren());
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return itemCount;
    }

    /**
     * Set the Box connection using a userId
     * @param userId
     */
    private void setBoxConnection(String userId){
        try {
            // Create JWT Encryption preferences
            URL url = Resources.getResource(boxPrivateKeyPath);
            String privateKey = Resources.toString(url, Charsets.UTF_8);
            JWTEncryptionPreferences encryptionPref = new JWTEncryptionPreferences();
            encryptionPref.setPublicKeyID(boxPublicKeyId);
            encryptionPref.setPrivateKey(privateKey);
            encryptionPref.setPrivateKeyPassword(boxKeyPassphrase);
            encryptionPref.setEncryptionAlgorithm(EncryptionAlgorithm.RSA_SHA_256);
            IAccessTokenCache accessTokenCache = new InMemoryLRUAccessTokenCache(MAX_CACHE_ENTRIES);

            this.api = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(
                    boxEnterpriseId, boxClientId, boxClientSecret, encryptionPref, accessTokenCache);
            BoxUser user = new BoxUser(api, userId);

            this.api = BoxDeveloperEditionAPIConnection.getAppUserConnection(
                    user.getInfo().getLogin(), boxClientId, boxClientSecret, encryptionPref, accessTokenCache);


        } catch (IOException e) {
            e.printStackTrace();

        }

    }


}
