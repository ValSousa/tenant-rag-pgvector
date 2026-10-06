# Gabarito dos documentos de exemplo (RF-011)

Respostas esperadas para as perguntas da seção 10 do README, por cliente, conforme o texto dos PDFs de `documents/cliente-a/` e `documents/cliente-b/`. Todos os dados são fictícios (RF-011 RN-01).

Usado por:

- `SampleDocumentsIT` (CT-101 e CT-102), que lê as seções abaixo;
- `src/test/resources/ddt/gabarito-perguntas.csv` (CT-075, opt-in com OpenAI real), cujos termos saem daqui;
- validação manual das respostas do `/ask` (RF-008).

Se um PDF mudar, atualize este gabarito e o `gabarito-perguntas.csv` junto.

## Documentos

| Cliente | Segurado | Apólice | Sinistro | Veículo | Placa | Arquivos |
|---|---|---|---|---|---|---|
| A | Paulo Almeida | AP-2026-00482 | SIN-2026-01873 | Toyota Corolla XEi 2024 | PQR-4A27 | `contrato.pdf` (CONTRACT), `sinistro.pdf` (CLAIM), `vistoria.pdf` (INSPECTION) |
| B | Ricardo Martins | AP-2026-00731 | SIN-2026-02461 | Honda Civic Touring 2023 | RIC-7B42 | `contrato.pdf` (CONTRACT), `sinistro.pdf` (CLAIM), `vistoria.pdf` (INSPECTION) |

## Respostas — Cliente A

| # | Pergunta | Resposta esperada | Fonte |
|---|---|---|---|
| 1 | Qual é o valor da franquia da apólice? | R$ 4.000,00 (eventos de colisão). | CONTRACT |
| 2 | Quais danos foram identificados na vistoria? | Porta dianteira esquerda, porta traseira esquerda, para-lama dianteiro esquerdo, roda dianteira esquerda, suspensão dianteira esquerda e painel lateral esquerdo (substituição); longarina dianteira esquerda (reparo estrutural); pintura da lateral esquerda (reparação). | INSPECTION |
| 3 | Quando ocorreu o sinistro? | 18/09/2026, por volta das 17h40. | CLAIM |
| 4 | O custo estimado do reparo representa qual percentual do valor do veículo? | 80%: R$ 96.000,00 de reparo sobre R$ 120.000,00 de valor de referência. | INSPECTION |
| 5 | O sinistro pode ser considerado perda total segundo as regras da apólice? | **Sim.** O contrato caracteriza perda total quando o reparo ultrapassa 75% do valor de referência; a vistoria estima 80%. | CONTRACT + INSPECTION |

## Respostas — Cliente B

| # | Pergunta | Resposta esperada | Fonte |
|---|---|---|---|
| 1 | Qual é o valor da franquia da apólice? | R$ 5.500,00 (eventos de colisão). | CONTRACT |
| 2 | Quais danos foram identificados na vistoria? | Para-choque dianteiro, capô, farol dianteiro direito e radiador (substituição); condensador do ar-condicionado e travessa dianteira (reparo). | INSPECTION |
| 3 | Quando ocorreu o sinistro? | 22/09/2026, por volta das 14h15. | CLAIM |
| 4 | O custo estimado do reparo representa qual percentual do valor do veículo? | Aproximadamente 53,7%: R$ 51.000,00 de reparo sobre R$ 95.000,00 de valor de referência. | INSPECTION |
| 5 | O sinistro pode ser considerado perda total segundo as regras da apólice? | **Não.** O contrato exige que o reparo ultrapasse 70% do valor de referência; a vistoria estima cerca de 53,7%. | CONTRACT + INSPECTION |

## Perda total

| Cliente | Limite do contrato | Reparo / valor de referência | Percentual | Perda total |
|---|---|---|---|---|
| A | 75% | R$ 96.000,00 / R$ 120.000,00 | 80% | Sim |
| B | 70% | R$ 51.000,00 / R$ 95.000,00 | 53,7% | Não |

## Valores exclusivos

Cada valor abaixo aparece só nos PDFs do próprio cliente (RF-011 RN-02, CA-03; CT-101). Um deles na resposta ou na busca do outro cliente indica vazamento entre clientes.

### Cliente A

- `Paulo Almeida`
- `AP-2026-00482`
- `SIN-2026-01873`
- `PQR-4A27`
- `Toyota Corolla`
- `R$ 4.000,00`
- `75%`
- `18/09/2026`
- `20/09/2026`
- `R$ 96.000,00`
- `R$ 120.000,00`
- `80%`

### Cliente B

- `Ricardo Martins`
- `AP-2026-00731`
- `SIN-2026-02461`
- `RIC-7B42`
- `Honda Civic`
- `R$ 5.500,00`
- `70%`
- `22/09/2026`
- `24/09/2026`
- `R$ 51.000,00`
- `R$ 95.000,00`
- `53,7%`
