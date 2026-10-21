package com.example.compatchecker.data.remote

import com.example.compatchecker.domain.model.Comment
import com.example.compatchecker.domain.model.CommentType
import com.example.compatchecker.domain.model.CompatibilityRule
import com.example.compatchecker.domain.model.CompatibilityStatus
import com.example.compatchecker.domain.model.ConfidenceLevel
import com.example.compatchecker.domain.model.Contribution
import com.example.compatchecker.domain.model.ContributionStatus
import com.example.compatchecker.domain.model.EvidenceSource
import com.example.compatchecker.domain.model.EvidenceType
import com.example.compatchecker.domain.model.GuideDifficulty
import com.example.compatchecker.domain.model.GuideStep
import com.example.compatchecker.domain.model.HardwareVariant
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.ProductCategory
import com.example.compatchecker.domain.model.ProductRequest
import com.example.compatchecker.domain.model.ProductSpecification
import com.example.compatchecker.domain.model.RepairGuide
import com.example.compatchecker.domain.model.RequestStatus
import com.example.compatchecker.domain.model.RiskLevel

object DemoCatalogDataSource {

    val demoProducts: List<Product> = listOf(
        Product(
            id = "prod_ip15pro",
            name = "Apple iPhone 15 Pro",
            brand = "Apple",
            modelNumber = "A2849",
            category = ProductCategory.SMARTPHONE,
            releaseYear = 2023,
            formFactor = "Titanium Frame / Glass Sandwich (146.6 x 70.6 x 8.25 mm)",
            connectorType = "USB-C 3.2 Gen 2 (10Gbps DisplayPort)",
            summary = "6.1-inch Super Retina XDR smartphone powered by A17 Pro with modular back glass and USB-C port.",
            technicalNotes = "Features removable back glass assembly. Displays and batteries are cryptographically serialized to the logic board via Apple Secure Enclave. Swapping without calibration triggers warnings in iOS Settings.",
            aliases = listOf("iPhone 15 Pro", "A2849", "A3101", "A3102", "A3104", "iPhone16,1"),
            variants = listOf(
                HardwareVariant("A2849", "United States", "eSIM only (No physical nano-SIM tray)"),
                HardwareVariant("A3102", "Global / Europe", "Single nano-SIM + eSIM"),
                HardwareVariant("A3104", "China / HK", "Dual physical nano-SIM")
            ),
            specifications = listOf(
                ProductSpecification("SoC", "Apple A17 Pro (3nm TSMC)"),
                ProductSpecification("Display Connector", "44-pin staggered FPC header", isCriticalForFit = true),
                ProductSpecification("Battery Capacity", "3274 mAh (12.70 Wh)", "mAh"),
                ProductSpecification("Battery Voltage", "3.87 V nominal (4.48 V max)", "V", isCriticalForFit = true),
                ProductSpecification("Charging Port", "Modular USB-C daughterboard with bonded microphone", isCriticalForFit = true)
            )
        ),
        Product(
            id = "prod_ip14pro",
            name = "Apple iPhone 14 Pro",
            brand = "Apple",
            modelNumber = "A2650",
            category = ProductCategory.SMARTPHONE,
            releaseYear = 2022,
            formFactor = "Stainless Steel Frame (147.5 x 71.5 x 7.85 mm)",
            connectorType = "Lightning 2.0 (480Mbps)",
            summary = "6.1-inch Super Retina XDR smartphone with Dynamic Island and Lightning port.",
            technicalNotes = "Older chassis architecture requiring front display removal for internal access. Uses Lightning flex with integrated speaker contact pins.",
            aliases = listOf("iPhone 14 Pro", "A2650", "A2889", "A2890", "A2892", "iPhone15,2"),
            variants = listOf(
                HardwareVariant("A2650", "United States", "eSIM only"),
                HardwareVariant("A2890", "Global", "Single nano-SIM + eSIM")
            ),
            specifications = listOf(
                ProductSpecification("SoC", "Apple A16 Bionic (4nm TSMC)"),
                ProductSpecification("Display Connector", "40-pin dual-row FPC header", isCriticalForFit = true),
                ProductSpecification("Battery Capacity", "3200 mAh (12.38 Wh)", "mAh"),
                ProductSpecification("Charging Port", "Lightning flex sub-assembly", isCriticalForFit = true)
            )
        ),
        Product(
            id = "prod_s24ultra",
            name = "Samsung Galaxy S24 Ultra",
            brand = "Samsung",
            modelNumber = "SM-S928B",
            category = ProductCategory.SMARTPHONE,
            releaseYear = 2024,
            formFactor = "Titanium Frame / Flat Corning Gorilla Armor (162.3 x 79.0 x 8.6 mm)",
            connectorType = "USB-C 3.2 Gen 1 (DisplayPort 4K60)",
            summary = "6.8-inch Dynamic AMOLED 2X flagship smartphone with flat anti-reflective front display and S-Pen.",
            technicalNotes = "Completely flat display panel allows simplified glass laminations compared to S23 Ultra curved edges. Sub-board charging port is modular and connected via main RF coax flex.",
            aliases = listOf("Galaxy S24 Ultra", "SM-S928B", "SM-S928U", "SM-S9280", "SM-S928W", "Eureka Ultra"),
            variants = listOf(
                HardwareVariant("SM-S928B/DS", "Global", "Dual SIM (nano-SIM + eSIM)"),
                HardwareVariant("SM-S928U", "USA Carrier", "Single SIM + eSIM with mmWave 5G antennas"),
                HardwareVariant("SM-S928U1", "USA Unlocked", "Single SIM + eSIM")
            ),
            specifications = listOf(
                ProductSpecification("Display Type", "Flat 6.8\" Dynamic AMOLED 2X 120Hz LTPO", isCriticalForFit = true),
                ProductSpecification("Display FPC Connector", "50-pin Micro-Coax Hirose BM28", isCriticalForFit = true),
                ProductSpecification("Battery Model", "EB-BS928ABY", isCriticalForFit = true),
                ProductSpecification("Battery Capacity", "5000 mAh (19.40 Wh)", "mAh"),
                ProductSpecification("Main Camera Sensor", "200 MP ISOCELL HP2SX (1/1.3\")", isCriticalForFit = true)
            )
        ),
        Product(
            id = "prod_s23ultra",
            name = "Samsung Galaxy S23 Ultra",
            brand = "Samsung",
            modelNumber = "SM-S918B",
            category = ProductCategory.SMARTPHONE,
            releaseYear = 2023,
            formFactor = "Armor Aluminum Frame / Curved Edge Glass (163.4 x 78.1 x 8.9 mm)",
            connectorType = "USB-C 3.2 Gen 1",
            summary = "6.8-inch Dynamic AMOLED 2X curved display smartphone with S-Pen.",
            technicalNotes = "Curved lateral display chassis. Battery utilizes pull-tab adhesive pouch. Camera housing cutout dimensions differ by 1.8mm from S24 Ultra.",
            aliases = listOf("Galaxy S23 Ultra", "SM-S918B", "SM-S918U", "SM-S9180", "DM3"),
            variants = listOf(
                HardwareVariant("SM-S918B/DS", "Global", "Dual nano-SIM"),
                HardwareVariant("SM-S918U", "USA", "Single nano-SIM")
            ),
            specifications = listOf(
                ProductSpecification("Display Type", "Curved 6.8\" Dynamic AMOLED 2X 120Hz", isCriticalForFit = true),
                ProductSpecification("Display FPC Connector", "48-pin Hirose BM24 Header", isCriticalForFit = true),
                ProductSpecification("Battery Model", "EB-BS918ABY", isCriticalForFit = true),
                ProductSpecification("Battery Capacity", "5000 mAh (19.25 Wh)", "mAh")
            )
        ),
        Product(
            id = "prod_pixel8pro",
            name = "Google Pixel 8 Pro",
            brand = "Google",
            modelNumber = "G9FPL",
            category = ProductCategory.SMARTPHONE,
            releaseYear = 2023,
            formFactor = "Polished Aluminum / Matte Back Glass (162.6 x 76.5 x 8.8 mm)",
            connectorType = "USB-C 3.2 (DisplayPort Alt Mode supported)",
            summary = "6.7-inch Super Actua flat OLED smartphone featuring Google Tensor G3 and thermal camera sensor.",
            technicalNotes = "Screen replacement requires Google Pixel Repair software calibration tool via fastboot WebUSB to calibrate the optical under-display fingerprint sensor.",
            aliases = listOf("Pixel 8 Pro", "G9FPL", "GC3VE", "G1MNW", "Husky"),
            variants = listOf(
                HardwareVariant("G9FPL", "United States / Sub-6 + mmWave", "Integrated mmWave antenna on top frame"),
                HardwareVariant("GC3VE", "Global / Sub-6", "Standard Sub-6 5G frame")
            ),
            specifications = listOf(
                ProductSpecification("SoC", "Google Tensor G3 (4nm Samsung)"),
                ProductSpecification("Display Connector", "42-pin Panasonic AXT4 header", isCriticalForFit = true),
                ProductSpecification("Battery Model", "G101-00277-01", isCriticalForFit = true),
                ProductSpecification("Battery Capacity", "5050 mAh (19.55 Wh)", "mAh")
            )
        ),
        Product(
            id = "prod_disp_s24u_oem",
            name = "OEM Galaxy S24 Ultra Flat OLED Display Assembly",
            brand = "Samsung Service Parts",
            modelNumber = "GH82-33719A",
            category = ProductCategory.REPLACEMENT_DISPLAY,
            releaseYear = 2024,
            formFactor = "Frame-mounted Service Pack Assembly",
            connectorType = "50-pin Hirose BM28 Connector",
            summary = "Genuine Samsung service pack display with pre-installed titanium midframe, ear speaker, and vibrator motor.",
            technicalNotes = "Factory pre-calibrated touch controller IC. Plug-and-play installation without requiring motherboard soldering.",
            aliases = listOf("GH82-33719A", "GH82-33720A", "S24 Ultra Service Pack Display"),
            variants = listOf(
                HardwareVariant("GH82-33719A", "Titanium Gray", "Global Frame (SM-S928B)"),
                HardwareVariant("GH82-33720A", "Titanium Black", "US Carrier Frame (SM-S928U)")
            ),
            specifications = listOf(
                ProductSpecification("Panel Technology", "LTPO Dynamic AMOLED 2X 1-120Hz"),
                ProductSpecification("Brightness Peak", "2600 nits", "nits"),
                ProductSpecification("Pin Count", "50 pins", "pins", isCriticalForFit = true),
                ProductSpecification("Digitizer IC", "Samsung S6SY792X integrated", isCriticalForFit = true)
            )
        ),
        Product(
            id = "prod_batt_ip15pro_oem",
            name = "OEM iPhone 15 Pro Replacement Battery",
            brand = "Apple Self Service",
            modelNumber = "A2870",
            category = ProductCategory.BATTERY,
            releaseYear = 2023,
            formFactor = "L-Shaped Li-ion Pouch with BMS PCB (104 x 62 x 4.6 mm)",
            connectorType = "Dual-screw compression terminal connector",
            summary = "Genuine 3274 mAh Li-ion battery with integrated Battery Management System (BMS).",
            technicalNotes = "Direct drop-in for iPhone 15 Pro. To preserve iOS Battery Health metric without 'Important Battery Message', serial pairing must be validated via Apple System Configuration tool or original BMS board micro-soldered.",
            aliases = listOf("A2870", "616-00941", "iPhone 15 Pro Battery"),
            variants = listOf(
                HardwareVariant("A2870", "Worldwide", "Universal iPhone 15 Pro battery cell")
            ),
            specifications = listOf(
                ProductSpecification("Nominal Voltage", "3.87 V", "V", isCriticalForFit = true),
                ProductSpecification("Charge Limit Voltage", "4.48 V", "V", isCriticalForFit = true),
                ProductSpecification("Capacity", "3274 mAh (12.70 Wh)", "mAh", isCriticalForFit = true),
                ProductSpecification("Chemistry", "Lithium-ion polymer")
            )
        ),
        Product(
            id = "prod_batt_s24u_oem",
            name = "OEM Galaxy S24 Ultra Battery Cell",
            brand = "Samsung SDI",
            modelNumber = "EB-BS928ABY",
            category = ProductCategory.BATTERY,
            releaseYear = 2024,
            formFactor = "Rectangular Pouch Cell with Pull Tab (88 x 64 x 5.8 mm)",
            connectorType = "Snap-on FPC header (3-pin sense + power lines)",
            summary = "Genuine 5000 mAh battery manufactured by Samsung SDI with pull-tab release sleeve.",
            technicalNotes = "Drop-in replacement for all Galaxy S24 Ultra variants (SM-S928B, SM-S928U, SM-S9280). Does NOT lock out fast charging when replaced.",
            aliases = listOf("EB-BS928ABY", "GH43-05187A"),
            specifications = listOf(
                ProductSpecification("Capacity", "5000 mAh (19.40 Wh)", "mAh"),
                ProductSpecification("Voltage", "3.88 V nominal / 4.45 V max", "V", isCriticalForFit = true),
                ProductSpecification("Safety Certification", "UL1642, CE, KC Mark")
            )
        )
    )

    val demoRules: List<CompatibilityRule> = listOf(
        CompatibilityRule(
            id = "rule_s24u_disp_oem",
            sourceProductId = "prod_disp_s24u_oem",
            targetProductId = "prod_s24ultra",
            sourceProductName = "OEM Galaxy S24 Ultra Flat OLED Display Assembly",
            targetProductName = "Samsung Galaxy S24 Ultra",
            status = CompatibilityStatus.COMPATIBLE,
            confidence = ConfidenceLevel.HIGH_VERIFIED,
            summary = "Direct OEM Drop-in Replacement with 100% plug-and-play functionality.",
            technicalDetails = "The 50-pin Hirose BM28 micro-coax connector mates directly with the main logic board. Digitizer, 2600 nit HDR brightness, 1-120Hz LTPO refresh rate, and optical fingerprint scanner operate without vendor lockouts.",
            requiredModifications = "No soldering or software pairing tools required. Optical fingerprint sensor may require standard recalibration code in *#0*# service menu.",
            riskLevel = RiskLevel.LOW,
            evidenceSources = listOf(
                EvidenceSource(
                    id = "ev_s24u_disp_1",
                    title = "Samsung Service Bulletin SEC-DS-2024-03: S24 Ultra Panel Pinout Specifications",
                    type = EvidenceType.OEM_MANUAL,
                    authorOrSource = "Samsung Electronics Mobile Service Division",
                    citation = "Section 4.2 'Front Display Assembly Pin Configuration and Replacement Procedure'",
                    url = "https://example.com/samsung-service/sec-ds-2024-03",
                    verifiedDate = "2024-02-15"
                ),
                EvidenceSource(
                    id = "ev_s24u_disp_2",
                    title = "iFixit Teardown Analysis: Galaxy S24 Ultra Repairability Benchmark",
                    type = EvidenceType.TEARDOWN_BENCHMARK,
                    authorOrSource = "iFixit Engineering Lab",
                    citation = "Step 8: Display Connector Cross-Variant Verification",
                    url = "https://ifixit.com/Teardown/Samsung+Galaxy+S24+Ultra+Teardown/170123",
                    verifiedDate = "2024-02-01"
                )
            )
        ),
        CompatibilityRule(
            id = "rule_s23u_disp_on_s24u",
            sourceProductId = "prod_s23ultra",
            targetProductId = "prod_s24ultra",
            sourceProductName = "Samsung Galaxy S23 Ultra Components",
            targetProductName = "Samsung Galaxy S24 Ultra",
            status = CompatibilityStatus.INCOMPATIBLE,
            confidence = ConfidenceLevel.HIGH_VERIFIED,
            summary = "Physically and electrically incompatible due to curved chassis geometry and revised 50-pin vs 48-pin FPC header.",
            technicalDetails = "Galaxy S23 Ultra display uses a curved 48-pin Hirose BM24 connector, whereas S24 Ultra uses a flat 50-pin BM28 connector. Attempting to force connector seating will bend logic board ground pins. In addition, frame screw boss alignments differ by 3.2mm.",
            requiredModifications = "Not possible. Do not attempt interchange.",
            riskLevel = RiskLevel.HIGH_SERIALIZED_LOCKOUT,
            evidenceSources = listOf(
                EvidenceSource(
                    id = "ev_s23_s24_mismatch",
                    title = "Micro-Inspection Teardown Report: S23 Ultra vs S24 Ultra Display Pinout",
                    type = EvidenceType.COMMUNITY_REPAIR_LAB,
                    authorOrSource = "Hugh Jeffreys Hardware Lab",
                    citation = "Video analysis: Samsung Galaxy S24 Ultra Part Swapping & Durability",
                    url = "https://youtube.com/watch?v=s24u_partswap_test",
                    verifiedDate = "2024-02-10"
                )
            )
        ),
        CompatibilityRule(
            id = "rule_ip15_batt_oem",
            sourceProductId = "prod_batt_ip15pro_oem",
            targetProductId = "prod_ip15pro",
            sourceProductName = "OEM iPhone 15 Pro Replacement Battery",
            targetProductName = "Apple iPhone 15 Pro",
            status = CompatibilityStatus.CONDITIONAL,
            confidence = ConfidenceLevel.HIGH_VERIFIED,
            summary = "Direct physical fit, but requires Apple System Configuration tool or original BMS tag-on board to restore Battery Health reporting.",
            technicalDetails = "Cell dimensions (104 x 62 x 4.6 mm) and dual-screw terminal connector match perfectly. However, the TI BQ27z561 fuel gauge IC is serialized to the Secure Enclave. Installing without calibration yields 'Unknown Part' badge in Settings, though charging and safety cutoffs operate normally.",
            requiredModifications = "Option A: Authorize via Apple Self Service Repair Web Configurator. Option B: Micro-solder original battery BMS circuit board onto the new pouch cell using spot welding strips.",
            riskLevel = RiskLevel.MODERATE,
            evidenceSources = listOf(
                EvidenceSource(
                    id = "ev_ip15_batt_1",
                    title = "Apple Self Service Repair Manual: iPhone 15 Pro (Document 035-00627)",
                    type = EvidenceType.OEM_MANUAL,
                    authorOrSource = "Apple Support Operations",
                    citation = "Chapter 3: Battery Removal, Adhesive Release, and System Configuration",
                    url = "https://support.apple.com/manuals/iphone-15-pro",
                    verifiedDate = "2023-10-12"
                ),
                EvidenceSource(
                    id = "ev_ip15_batt_2",
                    title = "iFixit Teardown: iPhone 15 Pro Battery BMS Cryptographic Cryptanalysis",
                    type = EvidenceType.TEARDOWN_BENCHMARK,
                    authorOrSource = "iFixit Diagnostics Team",
                    citation = "Report #BMS-15P-2023: Serial Cryptographic Token Handshake Validation",
                    url = "https://ifixit.com/News/82871/iphone-15-teardown",
                    verifiedDate = "2023-09-28"
                )
            )
        ),
        CompatibilityRule(
            id = "rule_ip14_batt_on_ip15",
            sourceProductId = "prod_ip14pro",
            targetProductId = "prod_ip15pro",
            sourceProductName = "Apple iPhone 14 Pro Components",
            targetProductName = "Apple iPhone 15 Pro",
            status = CompatibilityStatus.INCOMPATIBLE,
            confidence = ConfidenceLevel.HIGH_VERIFIED,
            summary = "Incompatible: Battery flex cable length is 6mm too short and connector screw standoff is shifted 90 degrees.",
            technicalDetails = "iPhone 14 Pro battery (A2889/A2650) utilizes a 3200 mAh pack with flex routed towards the middle-right rail. iPhone 15 Pro requires flex routed along the upper edge with a revised titanium screw anchor.",
            requiredModifications = "Not compatible.",
            riskLevel = RiskLevel.NONE,
            evidenceSources = listOf(
                EvidenceSource(
                    id = "ev_ip14_15_incompat",
                    title = "Independent Repair Technician Technical Bulletin #410",
                    type = EvidenceType.SERVICE_BULLETIN,
                    authorOrSource = "Repair Preservation Foundation Lab",
                    citation = "Cross-generational physical dimension matrix for iPhone 14 / 15 series",
                    url = "https://example.com/rpf/bulletin-410",
                    verifiedDate = "2023-11-05"
                )
            )
        ),
        CompatibilityRule(
            id = "rule_s24u_batt_oem",
            sourceProductId = "prod_batt_s24u_oem",
            targetProductId = "prod_s24ultra",
            sourceProductName = "OEM Galaxy S24 Ultra Battery Cell",
            targetProductName = "Samsung Galaxy S24 Ultra",
            status = CompatibilityStatus.COMPATIBLE,
            confidence = ConfidenceLevel.HIGH_VERIFIED,
            summary = "100% Plug-and-play compatible with full 45W Super Fast Charging 2.0 support.",
            technicalDetails = "The EB-BS928ABY battery contains authentic Samsung safety monitoring thermistors (10k NTC). The fuel gauge communicates over SMBus with standard PMIC commands.",
            requiredModifications = "Apply replacement pre-cut battery adhesive strips. Reset battery cycle stats via Samsung diagnostic dialer code *#9900# -> BatteryStats Reset (optional).",
            riskLevel = RiskLevel.NONE,
            evidenceSources = listOf(
                EvidenceSource(
                    id = "ev_s24u_batt_1",
                    title = "Samsung Electronics Official Service Guide SM-S928",
                    type = EvidenceType.OEM_MANUAL,
                    authorOrSource = "Samsung Customer Service Division",
                    citation = "Disassembly & Reassembly Guide: Battery Module (EB-BS928ABY)",
                    url = "https://example.com/samsung-service/sm-s928-batt",
                    verifiedDate = "2024-01-30"
                )
            )
        )
    )

    val demoRequests: List<ProductRequest> = listOf(
        ProductRequest(
            id = "req_1",
            targetDeviceName = "OnePlus 12 (CPH2581)",
            targetModelNumber = "CPH2581",
            componentType = ProductCategory.BATTERY,
            notes = "Test whether OnePlus 12 dual-cell 5400mAh battery can safely be used in OnePlus 12R (CPH2609). Check connector pinout and SuperVOOC 100W compatibility.",
            votesCount = 48,
            hasUserVoted = false,
            status = RequestStatus.UNDER_LAB_TEST
        ),
        ProductRequest(
            id = "req_2",
            targetDeviceName = "Google Pixel 8a",
            targetModelNumber = "G6GPR",
            componentType = ProductCategory.REPLACEMENT_DISPLAY,
            notes = "Verify if Pixel 8 standard 120Hz OLED display panel works with Pixel 8a 6.1\" 120Hz display flex.",
            votesCount = 35,
            hasUserVoted = false,
            status = RequestStatus.OPEN_VOTING
        ),
        ProductRequest(
            id = "req_3",
            targetDeviceName = "Apple iPhone 15 Plus",
            targetModelNumber = "A3094",
            componentType = ProductCategory.CHARGING_PORT_FLEX,
            notes = "Test whether iPhone 15 standard USB-C flex is swappable with iPhone 15 Plus USB-C flex.",
            votesCount = 22,
            hasUserVoted = false,
            status = RequestStatus.OPEN_VOTING
        )
    )

    val demoComments: List<Comment> = listOf(
        Comment(
            id = "com_1",
            productId = "prod_ip15pro",
            authorName = "Alex Rivera",
            authorRole = "Master Repair Tech (Level 3)",
            content = "CRITICAL WARNING: When replacing the rear glass on iPhone 15 Pro, be extremely gentle around the LiDAR flex at the top right corner. The flex is paper-thin and tearing it disables Face ID depth mapping.",
            type = CommentType.WARNING,
            upvotes = 34,
            hasUserUpvoted = false,
            createdAt = System.currentTimeMillis() - 86400000 * 3
        ),
        Comment(
            id = "com_2",
            productId = "prod_s24ultra",
            authorName = "Elena Rostova",
            authorRole = "Hardware Diagnostic Engineer",
            content = "For S24 Ultra display repairs: the flat glass design makes frame swaps significantly safer than S23 Ultra. No heat gun required on the side rails if you buy the OEM service pack with pre-mounted midframe (GH82-33719A).",
            type = CommentType.COMPATIBILITY_TIP,
            upvotes = 19,
            hasUserUpvoted = false,
            createdAt = System.currentTimeMillis() - 86400000 * 5
        ),
        Comment(
            id = "com_3",
            productId = "prod_pixel8pro",
            authorName = "Marcus Vance",
            authorRole = "Electronics Lab Technician",
            content = "Does anyone know if optical fingerprint calibration requires Android 14 stable or does it work on developer previews?",
            type = CommentType.QUESTION,
            upvotes = 7,
            hasUserUpvoted = false,
            createdAt = System.currentTimeMillis() - 86400000 * 2
        )
    )

    val demoContributions: List<Contribution> = listOf(
        Contribution(
            id = "contrib_1",
            title = "Pixel 8 Pro vs Pixel 7 Pro Display FPC Incompatibility Benchmark",
            sourceProductName = "Google Pixel 8 Pro Super Actua Panel",
            targetProductName = "Google Pixel 7 Pro",
            proposedStatus = CompatibilityStatus.INCOMPATIBLE,
            evidenceUrl = "https://github.com/repair-wiki/pixel-8-pro-pinout-notes",
            technicalNotes = "Verified via microscope: Pixel 8 Pro uses 42-pin Panasonic AXT4 with pin 17 VGH set to 15.2V. Pixel 7 Pro uses 40-pin header with 13.8V rail.",
            submitterEmail = "tech@ifixbenchmarks.org",
            status = ContributionStatus.ACCEPTED
        )
    )

    val demoGuides: List<RepairGuide> = listOf(
        RepairGuide(
            id = "guide_s24u_screen",
            productId = "prod_s24ultra",
            title = "Samsung Galaxy S24 Ultra Screen & Frame Service Pack Assembly",
            difficulty = GuideDifficulty.INTERMEDIATE,
            estimatedMinutes = 35,
            requiredTools = listOf("Phillips #00 Screwdriver", "iOpener / Heat Mat (80°C)", "Suction Handle", "Plastic Prying Spudgers", "ESD Tweezers"),
            safetyWarnings = listOf(
                "Never puncture or bend the battery cell during rear glass removal.",
                "Always disconnect the battery connector first before disconnecting the OLED FPC."
            ),
            steps = listOf(
                GuideStep(1, "Back Glass Heating & Separation", "Heat rear perimeter to 80°C for 4 minutes. Insert plastic pick at bottom edge next to USB-C port.", "Avoid inserting prying tool more than 3mm near the volume button ribbon."),
                GuideStep(2, "Wireless Charging Coil & Bracket Removal", "Remove 18 Phillips #00 screws securing the upper motherboard shield and wireless charging pad."),
                GuideStep(3, "Battery Disconnection", "Using a non-conductive nylon spudger, pry straight up on the battery connector.", "Do not use metal tweezers near battery terminal."),
                GuideStep(4, "Display FPC Transfer", "Disconnect the 50-pin Hirose display FPC. If using a service pack with pre-installed frame, transfer logic board, daughterboard, and battery.")
            )
        ),
        RepairGuide(
            id = "guide_ip15_battery",
            productId = "prod_ip15pro",
            title = "iPhone 15 Pro Battery Replacement with Pull-Tab Release",
            difficulty = GuideDifficulty.INTERMEDIATE,
            estimatedMinutes = 40,
            requiredTools = listOf("Pentalobe P2 Screwdriver", "Tri-point Y000 Screwdriver", "Suction Cup", "Isopropanol 99%", "Battery Adhesive Puller"),
            safetyWarnings = listOf(
                "Discharge battery below 25% before starting repair to prevent thermal runaway if accidentally punctured.",
                "Back glass is fragile titanium-bonded glass; apply steady suction without jerking."
            ),
            steps = listOf(
                GuideStep(1, "Remove P2 Pentalobe Screws", "Unscrew the two 6.8mm Pentalobe screws located on either side of the USB-C port."),
                GuideStep(2, "Open Rear Glass", "Heat back cover to 70°C. Apply suction cup and hinge rear cover open to the right side (like a book)."),
                GuideStep(3, "Disconnect Battery & Cowling", "Remove Tri-point Y000 screws holding the battery cowling. Pry battery terminal up."),
                GuideStep(4, "Stretch Release Adhesive", "Gently grasp the 3 adhesive pull-tabs at the bottom and pull flat at a low angle.")
            )
        )
    )
}
