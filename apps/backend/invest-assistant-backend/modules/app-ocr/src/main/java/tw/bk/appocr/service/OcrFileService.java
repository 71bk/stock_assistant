package tw.bk.appocr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.service.FileService;

@Service
@RequiredArgsConstructor
public class OcrFileService {
    private final FileService fileService;

    public byte[] loadFileBytes(Long userId, Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
        return fileService.loadBytes(userId, fileId);
    }
}
