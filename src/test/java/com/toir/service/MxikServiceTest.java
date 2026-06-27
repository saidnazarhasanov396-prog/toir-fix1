package com.toir.service;

import com.toir.dto.mxik.MxikDto;
import com.toir.dto.mxik.MxikNameCodeCountProjection;
import com.toir.dto.mxik.MxikNameCountProjection;
import com.toir.dto.mxik.MxikRequest;
import com.toir.entity.Mxik;
import com.toir.exception.RestException;
import com.toir.repository.MxikRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MxikServiceTest {

    @Mock
    MxikRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    MxikService service;

    @BeforeEach
    void setUp() {
        service = new MxikService(repository, auditBuilderService);
    }

    @Test
    void findAllSearchesAcrossMxikFieldsWithNormalizedPattern() {
        Mxik mxik = mxik(UUID.randomUUID(), "40112001001000000", "Ammonia");
        when(repository.search(eq("%ammonia%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mxik)));

        Page<MxikDto> result = service.findAll(" Ammonia ", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().kod()).isEqualTo("40112001001000000");
        verify(repository).search(eq("%ammonia%"), any(Pageable.class));
    }

    @Test
    void createStoresUzAssetsMxikShapeAndNormalizesKodAndType() {
        when(repository.existsActiveByKod("40112001001000000")).thenReturn(false);
        when(repository.save(any(Mxik.class))).thenAnswer(invocation -> {
            Mxik saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(request(" 40112001001000000 ", " product "));

        ArgumentCaptor<Mxik> captor = ArgumentCaptor.forClass(Mxik.class);
        verify(repository).save(captor.capture());
        Mxik saved = captor.getValue();
        assertThat(saved.getKod()).isEqualTo("40112001001000000");
        assertThat(saved.getType()).isEqualTo("PRODUCT");
        assertThat(saved.getName()).isEqualTo("Ammonia");
        assertThat(saved.getNameUzLatn()).isEqualTo("Ammiak");
        assertThat(saved.getNameRu()).isEqualTo("Аммиак");
        assertThat(saved.getGroupName()).isEqualTo("Chemicals");
        assertThat(saved.getClassName()).isEqualTo("Nitrogen compounds");
        assertThat(saved.getPositionName()).isEqualTo("Fertilizer inputs");
        assertThat(saved.getSubPositionName()).isEqualTo("Ammonia products");
        assertThat(saved.getBrandName()).isEqualTo("NAVOIYAZOT");
        assertThat(saved.getAttributeName()).isEqualTo("Liquid");
        assertThat(saved.getBarcode()).isEqualTo("1234567890123");
    }

    @Test
    void createRejectsDuplicateActiveKod() {
        when(repository.existsActiveByKod("40112001001000000")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("40112001001000000", "PRODUCT")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("40112001001000000");
                });

        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsDuplicateKodFromAnotherActiveRecord() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id))
                .thenReturn(Optional.of(mxik(id, "OLD", "Old name")));
        when(repository.existsActiveByKodAndIdNot("40112001001000000", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request("40112001001000000", "PRODUCT")))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(repository, never()).save(any());
    }

    @Test
    void deleteSoftDeletesMxik() {
        UUID id = UUID.randomUUID();
        Mxik mxik = mxik(id, "40112001001000000", "Ammonia");
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(mxik));
        when(repository.save(mxik)).thenReturn(mxik);

        service.delete(id);

        assertThat(mxik.isDeleted()).isTrue();
        verify(repository).save(mxik);
    }

    @Test
    void catalogMethodsValidateRequiredParentNamesAndDelegate() {
        when(repository.findGroupCounts()).thenReturn(List.of(nameCount("Chemicals", 2)));
        when(repository.findClassCounts("Chemicals")).thenReturn(List.of(nameCount("Nitrogen compounds", 1)));
        when(repository.findPositionCounts("Nitrogen compounds")).thenReturn(List.of(nameCount("Fertilizer inputs", 1)));
        when(repository.findSubPositionCounts("Fertilizer inputs")).thenReturn(List.of(nameCount("Ammonia products", 1)));
        when(repository.findMxikCounts("Ammonia products")).thenReturn(List.of(nameCodeCount("Ammonia", "40112001001000000", 1)));

        assertThat(service.listGroupCounts()).extracting(MxikNameCountProjection::getName).containsExactly("Chemicals");
        assertThat(service.listClassCounts(" Chemicals ")).extracting(MxikNameCountProjection::getName).containsExactly("Nitrogen compounds");
        assertThat(service.listPositionCounts(" Nitrogen compounds ")).extracting(MxikNameCountProjection::getName).containsExactly("Fertilizer inputs");
        assertThat(service.listSubPositionCounts(" Fertilizer inputs ")).extracting(MxikNameCountProjection::getName).containsExactly("Ammonia products");
        assertThat(service.listMxikCounts(" Ammonia products ")).extracting(MxikNameCodeCountProjection::getCode).containsExactly("40112001001000000");

        assertThatThrownBy(() -> service.listClassCounts(" "))
                .isInstanceOfSatisfying(RestException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private MxikRequest request(String kod, String type) {
        return new MxikRequest(
                " Ammonia ",
                " Ammiak ",
                " Аммиак ",
                kod,
                type,
                " Chemicals ",
                " Химикаты ",
                " Кимёвий моддалар ",
                " Nitrogen compounds ",
                " Соединения азота ",
                " Азот бирикмалари ",
                " Fertilizer inputs ",
                " Сырье для удобрений ",
                " Уғит хомашёси ",
                " Ammonia products ",
                " Аммиачная продукция ",
                " Аммиак маҳсулотлари ",
                " NAVOIYAZOT ",
                " Навоиазот ",
                " Навоиазот ",
                " Liquid ",
                " Жидкость ",
                " Суюқлик ",
                " 1234567890123 "
        );
    }

    private Mxik mxik(UUID id, String kod, String name) {
        Mxik mxik = new Mxik();
        mxik.setId(id);
        mxik.setKod(kod);
        mxik.setName(name);
        mxik.setType("PRODUCT");
        return mxik;
    }

    private MxikNameCountProjection nameCount(String name, long count) {
        return new MxikNameCountProjection() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private MxikNameCodeCountProjection nameCodeCount(String name, String code, long count) {
        return new MxikNameCodeCountProjection() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getCode() {
                return code;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
